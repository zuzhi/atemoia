(ns atemoia.main-csv
  (:require
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [ring.core.protocols]
    [hiccup2.core :as h]
    [ring.adapter.jetty :as jetty])
  (:import (java.io StringWriter)
           (org.eclipse.jetty.server Server)))


(defn handler
  [{:keys [uri]}]
  (if (string/ends-with? uri ".csv")
    (let [csv-as-str (str (with-open [w (StringWriter.)]
                            (csv/write-csv w [["a" "b" "c"]
                                              ["a1" "b1" "c1"]
                                              ["a2" "b2" "c2"]])
                            w))]
      (def _csv-as-str csv-as-str)
      {:body    (reify ring.core.protocols/StreamableResponseBody
                  (write-body-to-stream [this response output-stream]
                    (with-open [w (io/writer output-stream)]
                      (.write w csv-as-str))))
       :headers {;; Optional: customize the default file name etc.
                 ;; see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition#directives
                 #_#_"Content-Disposition" "attachment; filename=\"cool.html\""
                 "Content-Type" "text/csv"}
       :status  200})
    (let []
      {:body    (->> [:html
                      [:head
                       [:title "hello"]]
                      [:body
                       [:pre (pr-str uri)]
                       [:a {:href "/data.csv"}
                        "data"]
                       [:div "OK!"]]]
                  (h/html {:mode :html})
                  (str "<!DOCTYPE html>\n"))
       :headers {"Content-Type" "text/html"}
       :status  200})))

(defonce *server (atom nil))

(defn -main
  []
  (swap! *server
    (fn [server]
      (when (instance? Server server)
        (.stop ^Server server))
      (jetty/run-jetty #'handler {:port  8080
                                  :join? false}))))

(comment
  (-main)
  _csv-as-str)
