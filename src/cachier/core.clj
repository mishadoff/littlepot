(ns cachier.core
  (:import [java.util.concurrent.locks ReentrantReadWriteLock]))

(defn make-cache [data-batch-fn]
  (ref {:data-batch-fn data-batch-fn
        :batches-in-progress 0
        :batches-done 0
        :queue (clojure.lang.PersistentQueue/EMPTY)
        :cap 5}))

(defn cache-exhausted? [cache]
  (let [{:keys [cap queue]} @cache]
    (< (count queue) cap)))

(defn active-batch? [cache]
  (let [{:keys [batches-in-progress]} @cache]
    (pos? batches-in-progress)))



(defn- fill-cache [cache]
  (future
    (let [data ((:data-batch-fn @cache))]
      (dosync
       (alter cache
              (fn [cache-map]
                (-> cache-map
                    (update-in [:queue] (fn [q] (into q data)))
                    (update-in [:batches-in-progress] dec)
                    (update-in [:batches-done] inc))))))))

(defn hit [cache]
  (dosync
   (when (and (cache-exhausted? cache) 
              (not (active-batch? cache)))
     ;; mark progress batch
     (alter cache
            (fn [cache-map]
              (update-in cache-map [:batches-in-progress] inc)))
     ;; run background process to fill the data
     (fill-cache cache))
     (let [{:keys [data-batch-fn
                   batches-in-progress
                   queue
                   cap]} @cache]
       (if (not (empty? queue))
         (let [e (peek queue)]
           (alter cache (fn [cache-map]
                          (-> cache-map
                              (update-in [:queue] pop))))
           e)
         :no-data
         ))))

  
        
  
