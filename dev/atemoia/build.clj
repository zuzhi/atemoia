(ns atemoia.build
  (:require [clojure.tools.build.api :as b]
            [shadow.cljs.devtools.api :as shadow.api]
            [shadow.cljs.devtools.server :as shadow.server]))

(def lib 'atemoia/app)
(def class-dir "target/classes")
(def uber-file "target/atemoia.jar")

(defn -main
  [& _]
  (let [basis (b/create-basis {:project "deps.edn"})]
    (b/delete {:path "target"})
    (shadow.server/start!)
    (shadow.api/release :atemoia)
    (shadow.api/release :ssr)
    (shadow.server/stop!)
    (b/write-pom {:class-dir class-dir
                  :lib       lib
                  :version   "1.0.0"
                  :basis     basis})
    #_(b/copy-dir {:src-dirs   (:paths basis)
                   :target-dir class-dir})
    (b/compile-clj {:basis       basis
                    :class-dir   class-dir})
    (b/uber {:class-dir class-dir
             :main      'atemoia.server
             :uber-file uber-file
             :basis     basis})
    (shutdown-agents)))
