(ns littlepot.core-test
  (:require [clojure.test :refer :all]
            [littlepot.core :refer :all]))

(deftest test-auto-fill
  (testing "Default littlepot functionality"
    (let [batch-data-fn (fn []
                          (Thread/sleep 100)
                          (range 10))
          pot (make-pot batch-data-fn)]
      ;; trigger data population
      (is (= :no-data (cook pot)))
      ;; wait to retrieved fully
      (Thread/sleep 500)
      (is (= (range 10) (take 10 (repeatedly #(cook pot)))))
      ;; next portion is unavailable
      (is (every? #(= % :no-data) (take 100 (repeatedly #(cook pot)))))
      )))

(deftest test-next-args-fn
  (testing "Advance args function"
    (let [batch-data-fn (fn [page]
                          (Thread/sleep 100)
                          (get {1 [1 2 3 4 5]
                                2 [2 4 6 8 10]
                                3 [3 6 9 12 15]}
                               page []))
          args [1]
          next-args-fn (fn [[v]] [(inc v)])
          pot (make-pot batch-data-fn
                        :cap 1
                        :args args
                        :next-args-fn next-args-fn)]
      ;; trigger data population
      (is (= :no-data (cook pot)))
      ;; wait to retrieved fully
      (Thread/sleep 300)
      ;; ask for the first page
      (is (= [1 2 3 4 5] (take 5 (repeatedly #(cook pot)))))
      ;; next portion still cooking
      (is (every? #(= % :no-data) (take 10 (repeatedly #(cook pot)))))
      (Thread/sleep 300)
      ;; ask for the 2nd page
      (is (= [2 4 6 8 10] (take 5 (repeatedly #(cook pot)))))
      ;; next portion still cooking
      (is (every? #(= % :no-data) (take 10 (repeatedly #(cook pot)))))
      (Thread/sleep 300)
      ;; ask for the 3rd page
      (is (= [3 6 9 12 15] (take 5 (repeatedly #(cook pot)))))
      ;; next portion still cooking
      (is (every? #(= % :no-data) (take 10 (repeatedly #(cook pot)))))
      (Thread/sleep 300)
      ;; it's actually exhausted
      (is (every? #(= % :exhausted) (take 10 (repeatedly #(cook pot)))))
      )))

(deftest test-exhaustion
  (testing "Only three batches of data available."
    (let [number-of-batches (atom 3)
          batch-data-fn (fn []
                          (when (pos? @number-of-batches)
                            (Thread/sleep 100)
                            (swap! number-of-batches dec)
                            (range 10)))
          pot (make-pot batch-data-fn)]
      ;; trigger data population
      (is (= :no-data (cook pot)))
      (dotimes [batch-num 3]
        ;; wait to retrieved fully
        (Thread/sleep 500)
        (is (= (range 10) (take 10 (repeatedly #(cook pot)))))
        ;; next portion is unavailable
        (if (< batch-num 2)
          (is (every? #(= % :no-data)
                      (take 20 (repeatedly #(cook pot)))))
          (do
            (Thread/sleep 200)
            (is (every? #(= % :exhausted)
                        (take 20 (repeatedly #(cook pot))))))
          )))))

(deftest test-error-no-batches
  (testing "Provided batch function throws error"
    (let [batch-data-fn (fn []
                          (Thread/sleep 100)
                          (/ 1 0))
          pot (make-pot batch-data-fn)]
      ;; trigger data population
      (is (= :no-data (cook pot)))
      (Thread/sleep 500)
      (is (every? #(= % :exhausted) (take 20 (repeatedly #(cook pot)))))
      (is (not (nil? (:last-error @pot)))))))


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
        (is (= consumer-ones (:elements-returned @pot)))
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
