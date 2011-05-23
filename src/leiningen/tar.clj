(ns leiningen.tar
  (:use [leiningen.jar :only [jar]]
        [clojure.java.io :only [copy file]]
        [clojure.java.shell :only [sh]])
  (:import [org.apache.tools.tar TarOutputStream TarEntry]
           [java.io File FileOutputStream ByteArrayOutputStream]))

(defn entry-name [release-name f]
  (let [prefix (str (System/getProperty "user.dir") File/separator "(pkg)?"
                    File/separator "?")
        prefix (.replaceAll prefix "\\\\" "\\\\\\\\") ; WINDERS!!!!
        stripped (.replaceAll (.getAbsolutePath f) prefix "")]
    (str release-name File/separator stripped)))

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

(defn- git-commit [git-dir]
  (when (.exists git-dir)
    {:git-commit (.trim (:out (sh "git" "rev-parse" "HEAD")))}))

(defn build-info [project]
  (if-let [build-info (:build-info project)]
    build-info
    (let [hudson (when (System/getenv "BUILD_ID")
                   {:build-id (System/getenv "BUILD_ID")
                    :build-tag (System/getenv "BUILD_TAG")})
          git (git-commit (file (:root project) ".git"))]
      (merge hudson git))))

(defn- add-build-info [project]
  (let [build-file (file (:root project) "pkg" "build.clj")
        build-info (build-info project)]
    (when build-info
      (.deleteOnExit build-file)
      (spit build-file (str build-info "\n")))))

(defn tar [project]
  (add-build-info project)
  (let [release-name (str (:name project) "-" (:version project))
        jar-file (jar project)
        tar-file (file (:root project) (format "%s.tar" release-name))]
    (.delete tar-file)
    (with-open [tar (TarOutputStream. (FileOutputStream. tar-file))]
      (doseq [p (file-seq (file (:root project) "pkg"))]
        (add-file release-name tar p))
      (doseq [j (filter #(re-find #"\.jar$" (.getName %))
                        (.listFiles (file (:library-path project))))]
        (add-file release-name tar j))
      (add-file (str release-name File/separator "lib") tar (file jar-file)))
    (println "Wrote" (.getName tar-file))))
