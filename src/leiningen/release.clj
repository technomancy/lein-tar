(ns leiningen.release
  (:use [leiningen.jar :only [jar]]
        [clojure.contrib.io :only [file to-byte-array]])
  (:import [org.apache.tools.tar TarOutputStream TarEntry]
           [java.io FileOutputStream]))

(defn entry-name [release-name f]
  (str release-name "/" (subs (.getAbsolutePath f)
                              (inc (count (System/getProperty "user.dir"))))))

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
                                (or (:pkg-path project) "pkg")))]
        (add-file release-name tar p))
      (doseq [j (filter #(re-find #"\.jar$" (.getName %))
                        (.listFiles (file (:library-path project))))]
        (add-file release-name tar j))
      (add-file release-name tar (file jar-file)))
    (println "Wrote" (.getName tar-file))))
