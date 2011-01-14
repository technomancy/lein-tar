(ns leiningen.tar
  (:use [leiningen.jar :only [jar]]
        [clojure.java.io :only [copy]])
  (:import [org.apache.tools.tar TarOutputStream TarEntry]
           [java.io File FileOutputStream ByteArrayOutputStream]))

(defn entry-name [release-name f]
  (let [prefix (str (System/getProperty "user.dir") "/(pkg/)?")
        stripped (.replaceAll (.getAbsolutePath f) prefix "")]
    (str release-name "/" stripped)))

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

(defn- add-build-info [project]
  (when (System/getenv "BUILD_ID")
    (let [build-file (File. (:root project) "pkg/build.clj")]
      (.deleteOnExit build-file)
      (spit build-file
            (str {:build-id (System/getenv "BUILD_ID")
                  :build-tag (System/getenv "BUILD_TAG")} "\n")))))

(defn tar [project]
  (add-build-info project)
  (let [release-name (str (:name project) "-" (:version project))
        jar-file (jar project)
        tar-file (File. (format "%s/%s.tar" (:root project) release-name))]
    (.delete tar-file)
    (with-open [tar (TarOutputStream. (FileOutputStream. tar-file))]
      (doseq [p (file-seq (File. (:root project) "pkg"))]
        (add-file release-name tar p))
      (doseq [j (filter #(re-find #"\.jar$" (.getName %))
                        (.listFiles (File. (:library-path project))))]
        (add-file release-name tar j))
      (add-file (str release-name "/lib") tar (File. jar-file)))
    (println "Wrote" (.getName tar-file))))
