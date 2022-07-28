(ns atemoia.server
  (:gen-class)
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [hiccup2.core :as h]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.log :as log]
            [next.jdbc :as jdbc])
  (:import (java.net URI)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHolder)
           (org.eclipse.jetty.websocket.api Session WebSocketConnectionListener WebSocketListener)
           (org.eclipse.jetty.websocket.servlet WebSocketCreator WebSocketServlet WebSocketServletFactory)))

(set! *warn-on-reflection* true)

(defn database->jdbc-url
  [database-url]
  (let [uri (URI/create database-url)
        creds (.getUserInfo uri)
        old-query (some-> (.getQuery uri)
                    (string/split #"&"))
        auth-query (map (partial string/join "=")
                     (zipmap ["user" "password"] (string/split creds #":" 2)))
        base-uri (URI. "postgresql" nil
                   (.getHost uri) (.getPort uri) (.getPath uri)
                   (string/join "&" (concat old-query auth-query))
                   (.getFragment uri))]
    (str (URI. "jdbc" (str base-uri) nil))))

(defn index
  [_]
  (let [html [:html {:lang "en"}
              [:head
               [:meta {:charset "UTF-8"}]
               [:link {:rel "icon" :href "data:"}]
               [:meta {:name    "viewport"
                       :content "width=device-width, initial-scale=1.0"}]
               [:meta {:name    "theme-color"
                       :content "#000000"}]
               [:meta {:name    "description"
                       :content "A simple full-stack clojure app"}]
               [:title "atemoia"]]
              [:body
               [:div {:id "atemoia"} "loading ..."]
               [:script {:src "/atemoia/main.js"}]]]]
    {:body    (->> html
                (h/html {:mode :html})
                (str "<!DOCTYPE html>\n"))
     :headers {"Content-Type" "text/html"}
     :status  200}))

(defn list-todo
  [{::keys [atm-conn]}]
  (let [response (jdbc/execute! atm-conn
                   ["SELECT * FROM todo"])]
    {:body    (json/generate-string response)
     :headers {"Content-Type" "application/json"}
     :status  200}))

(defn create-todo
  [{::keys [atm-conn]
    :keys  [body]}]
  (let [note (some-> body
               io/reader
               (json/parse-stream true)
               :note)]
    (jdbc/execute! atm-conn
      ["INSERT INTO todo (note) VALUES (?);
        DELETE FROM todo WHERE id IN (SELECT id FROM todo ORDER BY id DESC OFFSET 10)"
       note])
    {:status 201}))

(defn install-schema
  [{::keys [atm-conn]}]
  (jdbc/execute! atm-conn
    ["CREATE TABLE todo (id serial, note text)"])
  {:status 202})

(def routes
  `#{["/" :get index]
     ["/todo" :get list-todo]
     ["/todo" :post create-todo]
     ["/install-schema" :post install-schema]})

(defn create-service
  [service-map]
  (-> service-map
    (assoc ::http/secure-headers {:content-security-policy-settings ""}
           ::http/resource-path "public"
           ::http/routes (fn []
                           (route/expand-routes routes)))
    http/default-interceptors
    (update ::http/interceptors
      (partial cons
        (interceptor/interceptor {:enter (fn [ctx]
                                           (update ctx :request merge service-map))})))))


(defn with-ws-endpoint
  [^ServletContextHandler ctx path ^WebSocketCreator creator]
  (.addServlet ctx (ServletHolder.
                     (proxy [WebSocketServlet] []
                       (configure [^WebSocketServletFactory factory]
                         (.setCreator factory creator))))
    (str path))
  ctx)



(defonce state
  (atom nil))


(defn -main
  [& _]
  (let [port (Long/getLong "atemoia.server.http-port" 8080)
        database-url (System/getProperty "atemoia.server.atm-db-url"
                       "postgres://postgres:postgres@127.0.0.1:5432/postgres")
        atm-conn-jdbc-url (database->jdbc-url database-url)
        atm-conn {:jdbcUrl atm-conn-jdbc-url}
        *ws-clients (atom {})
        creator (reify WebSocketCreator
                  (createWebSocket [this req resp]
                    (let [*id (promise)]
                      (reify WebSocketConnectionListener
                        (onWebSocketConnect [this ws-session]
                          (let [id (random-uuid)]
                            (log/info :in "onWebSocketConnect"
                              :ws id)
                            (swap! *ws-clients assoc
                              @(deliver *id id) ws-session)
                            @(.sendStringByFuture (.getRemote ^Session ws-session)
                               (json/generate-string (jdbc/execute! atm-conn
                                                       ["SELECT * FROM todo"])))))
                        (onWebSocketClose [this status-code reason]
                          (log/info :in "onWebSocketClose"
                            :code status-code
                            :reason reason)
                          (swap! *ws-clients dissoc @*id))
                        (onWebSocketError [this cause]
                          (log/error :in "onWebSocketError"
                            :ws (deref *id 0 nil)
                            :exception cause))
                        WebSocketListener
                        (onWebSocketText [this msg]
                          (log/info :in "onWebSocketText"
                            :msg msg)
                          #_@(.sendStringByFuture (.getRemote ^Session @*conn)
                               (str "echo - " ring-request)))
                        (onWebSocketBinary [this payload offset length]
                          (log/info :in "onWebSocketBinary"
                            :payload (seq payload)))))))]
    (swap! state
      (fn [st]
        (some-> st http/stop)
        (-> {::http/port              port
             ::atm-conn               atm-conn
             ::http/file-path         "target/classes/public"
             ::http/host              "0.0.0.0"
             ::http/type              :jetty
             ::http/container-options {:context-configurator (fn [ctx]
                                                               (with-ws-endpoint ctx "/ws" creator))}
             ::http/join?             false}
          create-service
          http/dev-interceptors
          http/create-server
          http/start)))
    (println "started: " port)))

(defn dev-main
  [& _]
  ;; docker run --name my-postgres --env=POSTGRES_PASSWORD=postgres --rm -p 5432:5432 postgres:alpine
  (-> `shadow.cljs.devtools.server/start!
    requiring-resolve
    (apply []))
  (-> `shadow.cljs.devtools.api/watch
    requiring-resolve
    (apply [:atemoia]))
  (-main))

(comment
  (dev-main))
