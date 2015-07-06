(ns littlepot.core)

(defn- pot-under-cap?
  "Check whether data left in pot less than specified cap."
  [pot]
  (let [{:keys [cap queue]} @pot]
    (<= (count queue) cap)))

(defn- active-batch?
  "Check whether at least one active batch."
  [pot]
  (let [{:keys [batches-in-progress]} @pot]
    (pos? batches-in-progress)))

(defn- exhausted?
  "Indicates that pot can not produce data anymore."
  [pot]
  (:exhausted @pot))

(defn- pot-start-progress
  "Increment number of active batches."
  [pot]
  (dosync
   (alter pot (fn [pot-map]
                (update-in pot-map [:batches-in-progress] inc)))))

(defn- fill-pot
  "Returns future which retrieves data batch and adds it to the pot."
  [pot]
  (future
    (let [[data status]
          (try [((:data-batch-fn @pot)) :success]
               (catch Exception e ["error" :failed]))]
      (dosync
       (cond
         ;; request succesfull and data available
         (and (= :success status) (seq data))
         (alter pot
                (fn [pot-map]
                  (-> pot-map
                      (update-in [:queue] (fn [q] (into q data)))
                      (update-in [:batches-in-progress] dec)
                      (update-in [:batches-done] inc))))
         ;; request succesful but no data
         (and (= :success status) (empty? data))
         (alter pot
                (fn [pot-map]
                  (-> pot-map
                      (update-in [:batches-in-progress] dec)
                      (update-in [:batches-done] inc)
                      (assoc :exhausted true))))
         ;; some error occures
         (= status :failed)
         (alter pot
                (fn [pot-map]
                  (-> pot-map
                      (update-in [:batches-in-progress] dec)
                      (update-in [:batches-done] inc)
                      (assoc :last-error data)
                      (assoc :exhausted true))))
         :else (throw (IllegalArgumentException. "Invalid State"))
         )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Public API ;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-pot
  "Constructs a pot, queue-backed ref, which autofilled when exhausted."
  [data-batch-fn & {:keys [cap]
                    :or {cap 10}}]
  (ref {:data-batch-fn data-batch-fn
        :batches-in-progress 0
        :batches-done 0
        :queue (clojure.lang.PersistentQueue/EMPTY)
        :cap cap
        :last-error nil
        :exhausted nil
        }))

(defn cook
  "Retrieves data from pot immediately.
  If no data available, returns :no-data."
  [pot]
  (dosync
   (when (and (pot-under-cap? pot)
              (not (active-batch? pot))
              (not (exhausted? pot)))
     ;; FIXME move progress to fill pot?
     (pot-start-progress pot)
     (fill-pot pot))
   (let [{:keys [queue cap]} @pot]
     (cond
       ;; pot has data
       (seq queue)
       (let [e (peek queue)]
         (alter pot (fn [pot-map]
                      (update-in pot-map [:queue] pop)))
         e)
       ;; pot has no data and it is not produce anything
       (and (empty? queue) (exhausted? pot))
       :exhausted
       ;; data appears soon
       :else :no-data
       ))))
