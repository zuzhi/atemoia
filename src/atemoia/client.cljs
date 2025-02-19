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
     ;;[:p "list"]
     (when error
       [:<>
        [:pre (str error ", try install schema")]
        [:button {:on-click (fn []
                              (js/fetch "/install-schema"
                                #js{:method "POST"}))}
         "install schema"]])
     [:ul
      (for [{:todo/keys [id note]} todos]
        ;; let first-id be first todo's id, make first-id red, second-id yellow
        (let [first-id (-> todos
                           first
                           :todo/id)
              second-id (-> todos
                            second
                            :todo/id)
              last-id (-> todos
                          last
                          :todo/id)]
          [:li {:value id}
           [:span {:style {:color (if (= first-id id) "red"
                                      (if (= second-id id) "tan"
                                          (if (= last-id id) "green"
                                              "black")))}}
            note]]))]
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
                      ;;(set! (.-disabled note-el) true)
                      (-> (js/fetch "/todo" #js{:method "POST"
                                                :body   (js/JSON.stringify json-body)})
                        (.then (fn [response]
                                 (unlock (.-ok response))))
                        (.catch (fn [ex]
                                  (unlock false))))))}
      [:label
       "new: "
       [:input {:name "note"
                :auto-focus true}]]]]))

(defonce *root (atom nil))

(defn after-load
  []
  (some-> @*root
    (.render (r/as-element [ui-root]))))

(defn start
  []
  (let [container (js/document.getElementById "atemoia")
        root (rc/createRoot container)]
    (fetch-todos)
    (.render root (r/as-element [ui-root]))
    (reset! *root root)))
