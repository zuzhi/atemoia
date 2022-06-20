(ns atemoia.client
  #?(:cljs (:require [reagent.core :as r]
                     [reagent.dom :as rd])))


#?(:cljs    (defonce *state (r/atom {}))
   :default (def ^:dynamic *state (atom {})))

(defn fetch-todos
  []
  #?(:cljs (-> (js/fetch "/todo")
             (.then (fn [response]
                      (when-not (.-ok response)
                        (throw (ex-info (.-statusText response)
                                 {:response response})))
                      (swap! *state dissoc :error)
                      (.json response)))
             (.then (fn [todos]
                      (swap! *state assoc :todos (js->clj todos
                                                   :keywordize-keys true))))
             (.catch (fn [ex]
                       (swap! *state assoc :error (ex-message ex)))))))

(defn ui-root
  []
  (let [{:keys [error todos]} @*state]
    [:div
     [:p "This is a sample clojure app to demonstrate how to use "
      [:a {:href "https://clojure.org/guides/tools_build"}
       "tools.build"]
      " to create and deploy a full-stack clojure app."]
     [:p "Checkout our "
      [:a {:href "https://github.com/souenzzo/atemoia"}
       "README"]]
     [:form
      {:on-submit (fn [evt]
                    (.preventDefault evt)
                    #?(:cljs (let [note-el (-> ^js evt
                                             .-target
                                             .-elements
                                             .-note)
                                   json-body #js{:note (.-value note-el)}
                                   unlock (fn [success?]
                                            (fetch-todos)
                                            (when success?
                                              (set! (.-value note-el) ""))
                                            (set! (.-disabled note-el) false))]
                               (set! (.-disabled note-el) true)
                               (-> (js/fetch "/todo" #js{:method "POST"
                                                         :body   (js/JSON.stringify json-body)})
                                 (.then (fn [response]
                                          (unlock (.-ok response))))
                                 (.catch (fn [ex]
                                           (unlock false)))))))}
      [:label
       "note: "
       [:input {:name "note"}]]]
     (when error
       [:<>
        [:pre (str error)]
        [:button {:on-click (fn []
                              #?(:cljs (js/fetch "/install-schema"
                                         #js{:method "POST"})))}
         "install schema"]])
     [:ul
      (for [{:todo/keys [id note]} todos]
        [:li {:key id}
         note])]]))

(defn start
  []
  #?(:cljs (let [root (js/document.getElementById "atemoia")]
             (when-not (some-> root
                         .-dataset
                         ^js .-initialState
                         js/JSON.parse
                         (js->clj :keywordize-keys true)
                         (->> (reset! *state)))
               (fetch-todos))
             (some->> root
               (rd/render [ui-root])))))

(defn after-load
  []
  #?(:cljs (some->> (js/document.getElementById "atemoia")
             (rd/render [ui-root]))))
