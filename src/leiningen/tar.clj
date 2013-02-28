(ns leiningen.tar
  (:use [leiningen.jar :only [jar]]
        [leiningen.uberjar :only [uberjar]]
        [clojure.java.io :only [copy file]])
  (:import [org.apache.tools.tar TarOutputStream TarEntry]
           [java.io File FileOutputStream ByteArrayOutputStream]))

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
      (copy f tar))
    (.closeEntry tar)))

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

(defn release-name [project]
  (str (:name project) "-" (:version project)))

(defn add-jars [project tar]
  (let [jar-file (jar project)]
    (doseq [:let [j (file jar-file)]
            f [(.getParentFile j) j]]
      (add-file (str (release-name project) "/lib") tar f))
    (doseq [j (jars-for project)]
      (add-file (release-name project) tar j))))

(defn add-uberjar [project tar]
  (let [uberjar-file (uberjar project)]
    (doseq [:let [j (file uberjar-file)]
            f [(.getParentFile j) j]]
      (add-file (str (release-name project) "/lib") tar f))))

(defn tar [project]
  (add-build-info project)
  (let [release-name (release-name project)
        tar-file (file (:root project) (format "%s.tar" release-name))]
    (.delete tar-file)
    (with-open [tar (TarOutputStream. (FileOutputStream. tar-file))]
      (.setLongFileMode tar TarOutputStream/LONGFILE_GNU)
      (doseq [p (file-seq (file (:root project) "pkg"))]
        (add-file release-name tar p))
      (if (get-in project [:tar :uberjar])
        (add-uberjar project tar)
        (add-jars project tar))
    (println "Wrote" (.getName tar-file)))))
