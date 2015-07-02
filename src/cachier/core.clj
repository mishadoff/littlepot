(ns cachier.core)

(defn make-cache [data-batch-fn]
  (atom {:data-batch-fn data-batch-fn
         :batches-in-progress 0
         :batches-done 0
         :queue (clojure.lang.PersistentQueue/EMPTY)
         :cap 5}))

(defn- batch-start [cache]
  (swap! cache
         (fn [cache-map]
           (update-in cache-map [:batches-in-progress] inc))))

(defn- batch-stop [cache data]
  (swap! cache
         (fn [cache-map]
           (-> cache-map
               (update-in [:queue] (fn [q] (into q data)))
               (update-in [:batches-in-progress] dec)
               (update-in [:batches-done] inc)))))


(defn- fill-cache [cache]
  (future
    (batch-start cache)
    (batch-stop cache ((:data-batch-fn @cache)))))

(defn hit [cache]
  (let [{:keys [data-batch-fn
                batches-in-progress
                queue
                cap]} @cache]
    ;; trigger cache fill when cap is reached
    (when (and (< (count queue) cap)
               (<= batches-in-progress 0))
      (fill-cache cache))
    (if (pos? (count queue))
      (let [e (peek queue)]
        (swap! cache (fn [cache-map]
                       (-> cache-map
                           (update-in [:queue] pop))))
        e)
      :no-data
      )))
