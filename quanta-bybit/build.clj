(ns build
  (:require
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as dd]))

(def lib 'io.github.clojure-quant/quanta-bybit)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(def pom-template
  [[:licenses
    [:license
     [:name "Eclipse Public License"]
     [:url "https://www.eclipse.org/legal/epl-v10.html"]]]
   [:developers
    [:developer
     [:name "clojure-quant"]]]
   [:scm
    [:url "https://github.com/clojure-quant/quanta-bybit/"]
    [:connection "scm:git:git://github.com/clojure-quant/quanta-bybit.git"]
    [:developerConnection "scm:git:ssh://git@github.com/clojure-quant/quanta-bybit.git"]]])

(def opts {:class-dir class-dir
           :lib lib
           :version version
           :basis basis
           :pom-data pom-template
           :src-dirs ["src"]})

(defn jar [_]
  (b/write-pom opts)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy "Deploy the JAR to Clojars." [_]
  (println "Deploying to Clojars..")
  (dd/deploy {:installer :remote
              :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))
              :artifact (b/resolve-path jar-file)}))
