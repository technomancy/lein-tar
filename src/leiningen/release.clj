(ns leiningen.release
  (:use [leiningen.jar :only [jar]]
        [clojure.contrib.io :only [file]])
  (:import [org.apache.tools.tar TarOutputStream TarEntry]
           [java.io FileOutputStream]))

(defn- add-file [tar f]
  (println "adding" f)
  (.putNextEntry tar (TarEntry. f)))

(defn release [project]
  (let [jar-file (jar project)
        tar-file (file (format "%s/%s-%s.tar" (:root project)
                               (:name project) (:version project)))]
    (with-open [tar (TarOutputStream. (FileOutputStream. tar-file))]
      (doseq [p (file-seq (file (:root project)
                                (or (:pkg-path project) "pkg")))]
        (add-file tar p))
      (doseq [j (filter #(re-find #"\.jar$" (.getName %))
                        (.listFiles (file (:library-path project))))]
        (add-file tar j))
      (add-file tar (file jar-file)))))
