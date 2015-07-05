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
    (let [data ((:data-batch-fn @pot))]
      (dosync
       (alter pot
              (fn [pot-map]
                (-> pot-map
                    (update-in [:queue] (fn [q] (into q data)))
                    (update-in [:batches-in-progress] dec)
                    (update-in [:batches-done] inc))))))))

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
        :cap cap}))

(defn cook
  "Retrieves data from pot immediately.
  If no data available, returns :no-data."
  [pot]
  (dosync
   (when (and (pot-under-cap? pot)
              (not (active-batch? pot)))
     (pot-start-progress pot)
     (fill-pot pot))
   (let [{:keys [queue cap]} @pot]
     (if-not (empty? queue)
       (let [e (peek queue)]
         (alter pot (fn [pot-map]
                      (update-in pot-map [:queue] pop)))
         e)
       :no-data
       ))))
