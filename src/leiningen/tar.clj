(ns leiningen.tar
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
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
        prefix (unix-path (str (System/getProperty "user.dir") "/(pkg|target)?/?"))
        stripped (.replaceAll f prefix "")]
    (str release-name "/"
         (if (.startsWith f (unix-path (str (System/getProperty "user.home")
                                            "/.m2")))
           (str (last (.split f "/")))
           stripped))))

(defn- add-file [dir-name tar f]
  (let [entry (doto (TarEntry. f)
                (.setName (entry-name dir-name f)))]
    (when (.canExecute f)
      ;; No way to expose unix perms? you've got to be kidding me, java!
      (.setMode entry 0755))
    (.putNextEntry tar entry)
    (when-not (.isDirectory f)
      (io/copy f tar))
    (.closeEntry tar)))

(defn- add-directory [dir-name tar]
  (let [entry (doto (TarEntry. dir-name))]
    (.putNextEntry tar entry)
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

(defn- find-jar [files]
  (if (map? files)
    ;; the jar task changed in Leiningen 2.1 to return map of
    ;; classifier/file so find the jar in the map
    (second (first (filter #(= [:extension "jar"] (key %)) files)))
    ;; ah, files *is* the jar
    files))

(defn generate-jar [project]
  (let [options (:tar project)]
    (if (:uberjar options)
      (uberjar/uberjar project)
      (find-jar (jar/jar project)))))

(defn add-jars [project tar dir-name jar]
  (let [options (:tar project)]
    (add-directory (str dir-name "/lib") tar)
    (add-file (str dir-name "/lib") tar (io/file jar))
    (if-not (:uberjar options)
      (doseq [j (jars-for project)]
        (add-file (str dir-name "/lib") tar j)))))

(defn- file-suffix
  "Take the name of given keyword fmt and replace every dash with a
  dot, so :tar-gz gets .tar.gz"
  [fmt]
  (string/replace (name fmt) #"-" "."))

(defn- out-stream [fmt tar-file]
  (let [file-stream (FileOutputStream. tar-file)]
    (case fmt
      (:tgz :tar-gz) (GZIPOutputStream. file-stream)
      file-stream)))

(defn- tar-name
  "Produce the name of the tar file based on the project or passed in
  arguments.  Falls back to the release-name in the case of no name
  passed in or inavlid arguments."
  [project [name-arg-key name-arg-val]]
  ;; seems overkill to pull in a cli parsing lib for just this...
  (if (and (or (= "-n" name-arg-key)
               (= "--name" name-arg-key))
           (not (empty? name-arg-val)))
    name-arg-val
    (release-name project)))

(defn tar [project & args]
  (add-build-info project)
  (let [options (:tar project)
        fmt (or (keyword (:format options)) :tar)
        output-dir (or (:output-dir options) (:target-path project))
        tar-name (tar-name project args)
        ;; jar/jar is an implicit project clean, so do this early
        jar (generate-jar project)
        tar-file (io/file output-dir
                          (format "%s.%s" tar-name (file-suffix fmt)))]
    (.delete tar-file)
    (.mkdirs (.getParentFile tar-file))
    (with-open [tar (TarOutputStream. (out-stream fmt tar-file))]
      (.setLongFileMode tar TarOutputStream/LONGFILE_GNU)
      ;; and add everything from pkg
      (doseq [p (file-seq (io/file (:root project) "pkg"))]
        (add-file tar-name tar p))
      ;; and whatever jars should be included
      (add-jars project tar tar-name jar)
      (println "Wrote" (.getName tar-file)))))
