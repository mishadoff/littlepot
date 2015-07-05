(ns littlepot.core-test
  (:require [clojure.test :refer :all]
            [littlepot.core :refer :all]))

(deftest test-auto-fill
  (testing "Default littlepot functionality"
    (let [batch-data-fn (fn []
                          (Thread/sleep 1000)
                          (range 10))
          pot (make-pot batch-data-fn)]
      ;; trigger data population
      (is (= :no-data (cook pot)))
      ;; wait to retrieved fully
      (Thread/sleep 1100)
      (is (= (range 10) (take 10 (repeatedly #(cook pot)))))
      ;; next portion is unavailable
      (is (every? #(= % :no-data) (take 100 (repeatedly #(cook pot)))))
      )))

(deftest test-multiple-consumers
  (testing "Simulate multiple consumers accessing one pot"
    (let [batch-latency (rand-int 1000)
          consumer-latency (rand-int 10)
          number-of-consumers 10
          batches-active (atom 0)
          batch-data-fn (fn []
                          (swap! batches-active inc)
                          (Thread/sleep batch-latency)
                          (let [data (repeat 1000 1)]
                            (swap! batches-active dec)
                            data))
          pot (make-pot batch-data-fn)
          run (atom true)
          consumer (fn []
                     (future
                       ;; (println "Consumer" id "started")
                       (loop [ones 0]
                         (if @run
                           (do
                             (Thread/sleep consumer-latency)
                             (let [e (cook pot)]
                               (cond
                                 (= e 1) (recur (inc ones))
                                 (= e :no-data) (recur ones)
                                 :else (throw (IllegalArgumentException.)))))
                           ones))))
          ;; track no more then 1 active batch
          invalid-active-batches (atom 0)
          _ (add-watch batches-active :no-more-than-one
                       (fn [_ _ _ new-value]
                         (when (> new-value 1)
                           (swap! invalid-active-batches inc))))
          ;; start consumers
          consumers (doall (take number-of-consumers (repeatedly consumer)))]
      ;; wait 10 seconds for consumers to work
      (Thread/sleep 10000)
      ;; request stop
      (reset! run false)
      ;; wait batches in progress become 0
      (Thread/sleep batch-latency)
      (is (zero? (:batches-in-progress @pot)))
      ;; no parallel batches detected
      (is (zero? @invalid-active-batches))
      (let [consumer-ones (reduce + (map deref consumers))
            total-produced (* 1000 (:batches-done @pot))
            left-in-pot (count (:queue @pot))]
        (println consumer-ones left-in-pot total-produced)
        (is (pos? total-produced))
        (is (= (+ consumer-ones left-in-pot) total-produced))
        (is (<= left-in-pot 1000))
        ))))

(deftest test-optionals
  (testing "Test cap"
    (let [batch-data-fn (fn []
                          (Thread/sleep 100)
                          (range 10))
          pot (make-pot batch-data-fn :cap 1)]
      ;; trigger pot cooking
      (is (= :no-data (cook pot)))
      ;; wait to fill
      (Thread/sleep 200)
      ;; first 9 values available
      (is (= (range 9) (take 9 (repeatedly #(cook pot)))))
      ;; there is only one left in pot
      (is (= 1 (count (:queue @pot))))
      ;; one batch was done and no one in progress
      (is (= 1 (:batches-done @pot)))
      (is (= 0 (:batches-in-progress @pot)))
      ;; retrieve last value and trigger cooking
      (is (= 9 (cook pot)))
      (Thread/sleep 200)
      (is (= 2 (:batches-done @pot)))
      (is (= (range 10) (take 10 (repeatedly #(cook pot)))))))
  )
