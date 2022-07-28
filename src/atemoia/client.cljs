(ns atemoia.client
  (:require ["react-dom/client" :as rc]
            [reagent.core :as r]))

(defonce state (r/atom {}))

(defn fetch-todos
  []
  (-> (js/fetch "/todo")
    (.then (fn [response]
             (when-not (.-ok response)
               (throw (ex-info (.-statusText response)
                        {:response response})))
             (swap! state dissoc :error)
             (.json response)))
    (.then (fn [todos]
             (swap! state assoc :todos (js->clj todos
                                         :keywordize-keys true))))
    (.catch (fn [ex]
              (swap! state assoc :error (ex-message ex))))))

(defn ui-root
  []
  (let [{:keys [error todos]} @state]
    [:<>
     [:p "This is a sample clojure app to demonstrate how to use "
      [:a {:href "https://clojure.org/guides/tools_build"}
       "tools.build"]
      " to create and deploy a full-stack clojure app."]
     [:p "Checkout our "
      [:a {:href "https://github.com/souenzzo/atemoia"}
       "README"]]
     [:form
      {:on-submit (fn [^js evt]
                    (.preventDefault evt)
                    (let [note-el (-> evt
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
                                  (unlock false))))))}
      [:label
       "note: "
       [:input {:name "note"}]]]
     (when error
       [:<>
        [:pre (str error)]
        [:button {:on-click (fn []
                              (js/fetch "/install-schema"
                                #js{:method "POST"}))}
         "install schema"]])
     [:ul
      (for [{:todo/keys [id note]} todos]
        [:li {:key id}
         note])]]))

(defonce *root (atom nil))

(defn after-load
  []
  (some-> @*root
    (.render (r/as-element [ui-root]))))

(defn start-ws
  []
  (let [ws (js/WebSocket. "ws://localhost:8080/ws")]
    (.addEventListener ws "open"
      (fn [evt] (js/console.log #js {:open evt})))
    (.addEventListener ws "message"
      (fn [evt]
        (swap! state assoc :todos (js->clj (js/JSON.parse (.-data evt))
                                    :keywordize-keys true))))
    (.addEventListener ws "error"
      (fn [evt] (js/console.error evt)))
    (.addEventListener ws "close"
      (fn [evt]
        #_(js/setTimeout start-ws 1000)
        (js/console.log #js {:close evt})))))

(defn start
  []
  (let [container (js/document.getElementById "atemoia")
        root (rc/createRoot container)]
    (start-ws)
    (fetch-todos)
    (.render root (r/as-element [ui-root]))
    (reset! *root root)))
