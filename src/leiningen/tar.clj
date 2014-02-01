(ns leiningen.tar
  (:require [clojure.java.io :as io]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.project :as project]
            [leiningen.jar :as jar]
            [leiningen.uberjar :as uberjar])
  (:import (java.io ByteArrayOutputStream File FileOutputStream)
           (java.util.zip GZIPOutputStream)
           (org.apache.tools.tar TarEntry TarOutputStream)))

(defn unix-path
  "Converts a File or String into a unix-like path"
  [f]
  (-> (if (instance? java.io.File f)
        (.getAbsolutePath f)
        f)
      (.replaceAll "\\\\" "/"))) ; WINDERS!!!!

(defn entry-name [release-name f]
  (let [f (unix-path f)
        prefix (unix-path (str (System/getProperty "user.dir") "/(pkg)?/?"))
        stripped (.replaceAll f prefix "")]
    (str release-name "/"
         (if (.startsWith f (unix-path (str (System/getProperty "user.home")
                                            "/.m2")))
           (str "lib/target/" (last (.split f "/")))
           stripped))))

(defn- add-file [release-name tar f]
  (let [entry (doto (TarEntry. f)
                (.setName (entry-name release-name f)))]
    (when (.canExecute f)
      ;; No way to expose unix perms? you've got to be kidding me, java!
      (.setMode entry 0755))
    (.putNextEntry tar entry)
    (when-not (.isDirectory f)
      (io/copy f tar))
    (.closeEntry tar)))

(defn- git-commit
  "Reads the value of HEAD and returns a commit SHA1."
  [git-dir]
  (when (.exists git-dir)
    (let [head (.trim (slurp (str (io/file git-dir "HEAD"))))]
      {:git-commit (if-let [ref-path (second (re-find #"ref: (\S+)" head))]
                     (.trim (slurp (str (io/file git-dir ref-path))))
                     head)})))

(defn build-info [project]
  (if-let [build-info (:build-info project)]
    build-info
    (let [hudson (when (System/getenv "BUILD_ID")
                   {:build-id (System/getenv "BUILD_ID")
                    :build-tag (System/getenv "BUILD_TAG")})
          git (git-commit (io/file (:root project) ".git"))]
      (merge hudson git))))

(defn- add-build-info [project]
  (let [pkg (io/file (:root project) "pkg")
        _ (.mkdir pkg)
        build-file (io/file pkg "build.clj")
        build-info (build-info project)]
    (when build-info
      (.deleteOnExit build-file)
      (spit build-file (str build-info "\n")))))

(defn jars-for [project]
  ;; see similar let in leiningen.uberjar/uberjar
  (let [whitelisted (select-keys project jar/whitelist-keys)
        project (merge (project/unmerge-profiles project [:default])
                       whitelisted)
        deps (->> (classpath/resolve-dependencies :dependencies project)
                  (filter #(.endsWith (.getName %) ".jar")))]
    deps))

(defn release-name [project]
  (str (:name project) "-" (:version project)))

;; jar task changed in Leiningen 2.1 to return map of classifier/file
(defn- jar-extension [files]
  (second (first (filter #(= [:extension "jar"] (key %)) files))))

(defn add-jars [project tar]
  (let [j (jar/jar project)
        jar-file (if (map? j) (jar-extension j) j)]
    (add-file (str (release-name project) "/lib") tar (io/file jar-file))
    (doseq [j (jars-for project)]
      (add-file (release-name project) tar j))))

(defn add-uberjar [project tar]
  (let [uberjar-file (uberjar/uberjar project)]
    (doseq [:let [j (io/file uberjar-file)]
            f [(.getParentFile j) j]]
      (add-file (str (release-name project) "/lib") tar f))))

(defn tar [project]
  (add-build-info project)
  (let [options (:tar project)
        fmt (or (keyword (:format options)) :tar)
        release-name (release-name project)
        tar-file (io/file (:root project) (format "%s.%s" release-name (name fmt))) ]

    (.delete tar-file)
    (with-open [tar (TarOutputStream. (case fmt
                                        :tgz (GZIPOutputStream.
                                              (FileOutputStream. tar-file))
                                        :tar (FileOutputStream. tar-file)))]
      (.setLongFileMode tar TarOutputStream/LONGFILE_GNU)
      (doseq [p (file-seq (io/file (:root project) "pkg"))]
        (add-file release-name tar p))
      (if (:uberjar options)
        (add-uberjar project tar)
        (add-jars project tar))
      (println "Wrote" (.getName tar-file)))))
