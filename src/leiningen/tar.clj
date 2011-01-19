(ns leiningen.tar
  (:use [leiningen.jar :only [jar]]
        [clojure.java.io :only [copy file]])
  (:import [org.apache.tools.tar TarOutputStream TarEntry]
           [java.io File FileOutputStream ByteArrayOutputStream]))

(defn entry-name [release-name f]
  (let [prefix (str (System/getProperty "user.dir") File/separator "(pkg)?"
                    File/separator "?")
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

(defn- add-build-info [project]
  (when (System/getenv "BUILD_ID")
    (let [build-file (file (:root project) "pkg" "build.clj")]
      (.deleteOnExit build-file)
      (spit build-file
            (str {:build-id (System/getenv "BUILD_ID")
                  :build-tag (System/getenv "BUILD_TAG")} "\n")))))

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
