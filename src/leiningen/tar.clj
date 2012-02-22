(ns leiningen.tar
  (:use [leiningen.jar :only [jar]]
        [clojure.java.io :only [copy file]])
  (:import [org.apache.tools.tar TarOutputStream TarEntry]
           [java.io File FileOutputStream ByteArrayOutputStream]))

(defn entry-name [release-name f]
  (let [prefix (str (System/getProperty "user.dir") File/separator
                    "(pkg)?" File/separator "?")
        prefix (.replaceAll prefix "\\\\" "\\\\\\\\") ; WINDERS!!!!
        stripped (.replaceAll (.getAbsolutePath f) prefix "")]
    (str release-name File/separator
         (if (.startsWith (str f) (str (System/getProperty "user.home")
                                       File/separator ".m2"))
           (str "lib/target/" (last (.split (str f) "/")))
           stripped))))

(defn- add-file [release-name tar f]
  (when-not (.isDirectory f)
    (let [entry (doto (TarEntry. f)
                  (.setName (entry-name release-name f)))
          baos (ByteArrayOutputStream.)]
      (when (.canExecute f)
        ;; No way to expose unix perms? you've got to be kidding me, java!
        (.setMode entry 0755))
      (copy f baos)
      (.putNextEntry tar entry)
      (.write tar (.toByteArray baos))
      (.closeEntry tar))))

(defn- git-commit
  "Reads the value of HEAD and returns a commit SHA1."
  [git-dir]
  (when (.exists git-dir)
    (let [head (.trim (slurp (str (file git-dir "HEAD"))))]
      {:git-commit (if-let [ref-path (second (re-find #"ref: (\S+)" head))]
                     (.trim (slurp (str (file git-dir ref-path))))
                     head)})))

(defn build-info [project]
  (if-let [build-info (:build-info project)]
    build-info
    (let [hudson (when (System/getenv "BUILD_ID")
                   {:build-id (System/getenv "BUILD_ID")
                    :build-tag (System/getenv "BUILD_TAG")})
          git (git-commit (file (:root project) ".git"))]
      (merge hudson git))))

(defn- add-build-info [project]
  (let [pkg (file (:root project) "pkg")
        _ (.mkdir pkg)
        build-file (file pkg "build.clj")
        build-info (build-info project)]
    (when build-info
      (.deleteOnExit build-file)
      (spit build-file (str build-info "\n")))))

(defn jars-for [project]
  (filter #(re-find #"\.jar$" (.getName %))
          (if-let [lib (:library-path project)]
            (.listFiles (file lib))
            (map file ((ns-resolve (doto 'leiningen.core.classpath require)
                                   'get-classpath) project)))))

(defn tar [project]
  (add-build-info project)
  (let [release-name (str (:name project) "-" (:version project))
        jar-file (jar project)
        tar-file (file (:root project) (format "%s.tar" release-name))]
    (.delete tar-file)
    (with-open [tar (TarOutputStream. (FileOutputStream. tar-file))]
      (doseq [p (file-seq (file (:root project) "pkg"))]
        (add-file release-name tar p))
      (doseq [j (jars-for project)]
        (add-file release-name tar j))
      (add-file (str release-name File/separator "lib") tar (file jar-file)))
    (println "Wrote" (.getName tar-file))))
