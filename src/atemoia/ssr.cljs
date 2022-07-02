(ns atemoia.ssr
  (:require ["react-dom/server" :as rds]
            [atemoia.client :as client]
            [reagent.core :as r]))

(defn ^:export render
  [state-json-str]
  (binding [client/state (r/atom (js->clj (js/JSON.parse state-json-str)
                                   :keywordize-keys true))]
    (rds/renderToString (r/as-element [client/ui-root]))))
