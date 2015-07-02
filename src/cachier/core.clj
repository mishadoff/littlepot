(ns cachier.core)

;; fill fn
(def data-batch (ref (fn []
                       (println "Batch started...")
                       (Thread/sleep 10000)
                       (println "Batch finished.")
                       (range 10))))

(def batch-in-progress (ref false))
;; queue
(def q (java.util.concurrent.LinkedBlockingQueue.))

(defn get-from-cache []
  (dosync
   ;; trigger cache fill whe cap is reached
   (when (and (< (count q) 5) (not @batch-in-progress))
     (future (dosync (doseq [d (data-batch)]
                       (.put q d))
                     (ref-set batch-in-progress false)))
     (ref-set batch-in-progress true))
   (if (pos? (count q))
     (.take q)
     :no-data
     )))
