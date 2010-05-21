(ns leiningen.release
  (:use [leiningen.jar :only [jar]]
        [clojure.contrib.io :only [file to-byte-array]])
  (:import [org.apache.tools.tar TarOutputStream TarEntry]
           [java.io FileOutputStream]))

(def root-count (inc (count (System/getProperty "user.dir"))))

(defn- add-file [tar f]
  (let [relative-file (java.io.File. (subs (.getAbsolutePath f) root-count))]
    (when-not (.isDirectory f)
      (.putNextEntry tar (TarEntry. relative-file))
      (.write tar (to-byte-array f))
      (.closeEntry tar))))

(defn release [project]
  (let [jar-file (jar project)
        tar-file (file (format "%s/%s-%s.tar" (:root project)
                               (:name project) (:version project)))]
    (.delete tar-file)
    (with-open [tar (TarOutputStream. (FileOutputStream. tar-file))]
      (doseq [p (file-seq (file (:root project)
                                (or (:pkg-path project) "pkg")))]
        (add-file tar p))
      (doseq [j (filter #(re-find #"\.jar$" (.getName %))
                        (.listFiles (file (:library-path project))))]
        (add-file tar j))
      (add-file tar (file jar-file)))
    (println "Wrote" (.getName tar-file))))
