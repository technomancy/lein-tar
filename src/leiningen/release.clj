(ns leiningen.release
  (:use [leiningen.jar :only [jar]]
        [clojure.contrib.io :only [file to-byte-array]]
        [clojure.contrib.string :only [replace-re]])
  (:import [org.apache.tools.tar TarOutputStream TarEntry]
           [java.io FileOutputStream]))

(defn entry-name [release-name f]
  (let [prefix (re-pattern (str (System/getProperty "user.dir") "/(pkg/)?"))
        stripped (replace-re prefix "" (.getAbsolutePath f))]
    (str release-name "/" stripped)))

(defn- add-file [release-name tar f]
  (when-not (.isDirectory f)
    (let [entry (doto (TarEntry. f)
                  (.setName (entry-name release-name f)))]
      (.putNextEntry tar entry)
      (.write tar (to-byte-array f))
      (.closeEntry tar))))

(defn release [project]
  (let [release-name (str (:name project) "-" (:version project))
        jar-file (jar project)
        tar-file (file (format "%s/%s.tar" (:root project) release-name))]
    (.delete tar-file)
    (with-open [tar (TarOutputStream. (FileOutputStream. tar-file))]
      (doseq [p (file-seq (file (:root project)
                                "pkg"))]
        (add-file release-name tar p))
      (doseq [j (filter #(re-find #"\.jar$" (.getName %))
                        (.listFiles (file (:library-path project))))]
        (add-file release-name tar j))
      (add-file (str release-name "/lib") tar (file jar-file)))
    (println "Wrote" (.getName tar-file))))
