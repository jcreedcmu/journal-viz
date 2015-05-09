(ns viz.core
    (:require [reagent.core :as reagent :refer [cursor atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [cljsjs.react :as react]
              [ajax.core :refer [GET]])
    (:import goog.History))

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to viz"]
   [:div [:a {:href "#/about"} "go to about page"]]])

(defn about-page []
  [:div [:h2 "About viz"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn account [name]
  [:td {:class name} name])

(defn amount-td [amount]
  (let [cleanup (fn [x] (.replace x (js* "/[$,]/g") ""))]
   (if-let [[_ bef aft] (.match amount #"(.+)\.(.*)" )]
     [[:td.right (cleanup bef) "."]
      [:td aft]]
     [[:td.right (cleanup amount) "."] [:td "00"]])))

(defn journal-row [row prev-row]
  (let [[date _ amount from to comment] row
        [prev-date _ amount from to comment] prev-row]
    `[~:tr
      ~[:td date]
      ~@(amount-td amount)
      ~[account from]
      ~[account to]
      ~[:td.tiny comment]]))

(defn blah [data]
  [:table (for [ix (range (min 200  (count data)))]
            ^{:key ix} [journal-row (get data ix)])])

(defn debug [] (swap! (cursor session/state [:data]) #(vec (rest %))))
(defn sortf [f]
  (fn [a b] (compare (f a) (f b))))
;;(pr  (get ) )

(def row-size 40)
(def window-size 500)

(defn visible-at [pos height scroll-pos]
  (and (>= (+ window-size scroll-pos) pos)
       (<= scroll-pos (+ pos height))))

(defn row-data [[date _ amount from to comment] [prev-date _ _ _ _ _]]
  [:span
   [:div.ib {:style {:width "1em"}} ""]
   [:div.ib {:class (if (= date prev-date) "same" "") :style {:width "8em"}} date]
   [:div.ib {:style {:text-align "right" :width "8em"}} (.replace amount (js* "/[$,]/g") " ")]
   [:div.ib {:style {:width "2em"}} ""]
   [:div.ib {:class (str "account " from)} from]
   [:div.ib {:style {:width "4em" :text-align "center"}} "➤"]
   [:div.ib {:class (str "account " to)} to]
   [:div.ib {:style {:width "2em"}} ""]
   [:div.ib {:style {:font-size "0.6em" :max-width "1em" :white-space "nowrap"}}  comment]])

(defn scroller [data at]
  (reagent/create-class
   {:component-did-mount
     (fn [this]
      (set! (.-scrollTop (-> this .getDOMNode)) at))
    :component-will-receive-props
    (fn [this [_ _data at]]
      (set! (.-scrollTop (-> this .getDOMNode)) at))
    :reagent-render
    (fn [data at]
      (let [num-rows (count data)
            begin (.floor js/Math (/ at row-size))
            end (.ceil js/Math (/ (+ at window-size) row-size))]
        [:div {:on-scroll (fn [this] (session/put! :pos (-> this .-target .-scrollTop)))
               :style {:background-color "#000"
                       :height (str window-size "px")
                       :overflow-y "auto"
                       :overflow-x "hidden"
                      :position "relative"}}

        [:div {:style {:height (str (* num-rows row-size) "px")}} ;; strut

         (for [x (range begin end)]

           ^{:key x} [:div.row {:style {
                                    :top (str (* x row-size) "px")
                                    :background-color (case (mod x 2) 0 "#eef4e3" 1 "#fdfbec")}}
                      [row-data (get data x) (get data (dec x))]])]]))}))


(defn got-journal-data [data]
  (session/put! :data data)
  (session/put! :clean-data (vec (sort (sortf #(get % 0))
                                       data))))

(defn got-report-data [[parents report]]
  (session/put! :parents-data parents)
  (session/put! :report-data report))

(defn reload-journal-data []
  (GET "/journal" {:handler got-journal-data
                   :response-format :json}))

(defn journal-page []
  (let [data (session/get :clean-data)]
    (if (not= 0 (count data))
      [:div
       [scroller (:clean-data @session/state) (session/get :pos)]]
      (do
        (reload-journal-data)
        [:div "Loading..."]))))

(defn get-rows [data account-info root]
  (vec (concat  [(merge {"which" root} (get account-info root))]
                (apply concat (for [kid (get data root)]
                                (if (contains? (session/get :expand) root)
                                  (get-rows data account-info kid)
                                  [])))))


  )
(defn insert-commas [x]
  (.replace x (js* "/(.)(..)/g") "$1 $2"))

(defn format-money [x]
  (let [[neg abs] (if (< x 0) [true (- x)] [false x])
        minus (if neg "-" "")
        zero (= x 0)
        fixed (.replace (.toFixed (/ abs 100) 2) #".(...)+\." insert-commas)
        text (str minus fixed)]
    (cond
      zero [:span.zero text]
      neg [:span.negative text]
      true text)))

(defn toggle-set [set x]
  (if (contains? set x) (disj set x) (conj set x)))

(defn account-row [ix row]
  (let [which (row "which")]
   (if (>= (row "depth") 0)
     [:div.report-row
      {:on-mouse-down (fn [e]
                        (session/update-in! [:expand] #(toggle-set % which))
                        (.preventDefault e))
       :style {:cursor "pointer" :background-color (case (mod ix 2) 0 "#eef4e3" 1 "#fdfbec")}}
      [:div.ib-plain {:style {:width "350px"}}
       (for [x (range (row "depth"))] ^{:key x} [:div.spacer ""])
       [:div {:style {:display "inline-block"} :class which}
        which
        (if (row "hasKids") (if ((session/get :expand) which) ":" "...")  "")]]
      [:div.ib-plain { :style {:width "200px" :text-align "right"}} (format-money (row "sum"))]]
     [:span])))

(defn reload-report-data []
  (GET "/report" {:handler got-report-data
                  :response-format :json}))

(defn report-page []
  (let [data (session/get :parents-data)
        account-rows (session/get :report-data)
        account-info (apply hash-map (apply concat (for [row account-rows]
                                                     [(row "which")
                                                      (dissoc row "which")])))
        display-rows (get-rows data account-info "Top")]

    (if (not= 0 (count data))
      [:span (for [ix (range (count display-rows))]
        (let [row (display-rows ix)]
          ^{:key (row "which")} [account-row ix row]))]


      (do (reload-report-data)
          [:div "Loading..."]))))

(defn reload-data []
  (reload-journal-data)
  (reload-report-data))

(defn index-page [] [:div [:ul.navbar
                           [:li [:a {:href "#" :on-click #(reload-data)} "Refresh"]]
                           [:li [:a {:href "#" :on-click #(session/put! :current #'journal-page)} "Ledger"]]
                           [:li [:a {:href "#" :on-click #(session/put! :current #'report-page)}"Report"]]]
                     [(session/get :current)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-root []
  (reagent/render [index-page] (.getElementById js/document "app")))

(defn init! []
  (session/put! :current  #'report-page)
  (session/put! :expand  #{"Top" "Int" "Ext"})
  (hook-browser-navigation!)
  (mount-root))
