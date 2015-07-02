(ns cachier.core)

;; TODO remove fill fn
(def data-batch (fn []
                  (println "Batch started...")
                  (Thread/sleep 10000)
                  (println "Batch finished.")
                  (range 10)))

;; TODO make a fn
(def cache
  (atom {:data-batch-fn data-batch
         :batches-in-progress 0
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
               (update-in [:batches-in-progress] dec)))))


(defn- fill-cache [cache]
  (future
    (println "fill-cache-start" cache)
    (batch-start cache)
    (println "fill-cache-after-start" cache)
    (batch-stop cache ((:data-batch-fn @cache)))
    (println "fill-cache-after-stop" cache)))

(defn hit [cache]
  (println "hit-start" cache)
  (let [{:keys [data-batch-fn
                batches-in-progress
                queue
                cap]} @cache]
    ;; trigger cache fill when cap is reached
    (println "hit-cap-check" cache)
    (when (and (< (count queue) cap)
               (<= batches-in-progress 0))
      (println "hit-before-fill" cache)
      (fill-cache cache)
      (println "hit-after-fill" cache))
    (println "before-get" cache)
    (if (pos? (count queue))
      (let [e (peek queue)]
        (swap! cache (fn [cache-map]
                       (-> cache-map
                           (update-in [:queue] pop))))
        (println "popped")
        e)
      :no-data
      )))
