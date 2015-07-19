# Tutorial

## No-Arg Batch Function

Consider we have some API endpoint to get 50 random images with tag _"cat"_

``` clojure
(def api-endpoint "http://image.provider.com/api/random?tag=cat")
```

Batched request will be implement as follows

``` clojure
(def get-batch []
  (-> api-endpoint
      http/get
      json/parse))
```

For this API `get-batch` is no-arg function, so we don't need any additional tweaks here. Just create a *pot*.

``` clojure
(def pot (make-pot get-batch))
```

We created a *pot*, no data send to image provider yet.

Request image

``` clojure
(cook pot) ;; => :no-data
```

Nothing available. It's because **littlepot** is *non-blocking*.
If we don't have data, we say `:no-data` immediately instead of waiting for availability. During the same time we have sent request to image provider to retrieve data in background.

``` clojure
(cook pot) ;; => :no-data
```

No data yet. Because our previous batched request still in progress.

*Note:* we don't send another batch request, because only one active batch allowed at a time.

``` clojure
(cook pot) ;; => {:url "http://image.provider.com/data/af342bb903.png"
                  :width "640"
                  :height "480"}
```

Yay, we've got an image.
And, by the way, another 49 images are cached in queue, so all next calls will return data immediately.

*Note:* It's easily composable, so to get 10 images at a time call function 10 times.

``` clojure
(take 10 (repeatedly #(cook pot)))
```

And you cooking and cooking, until the data exhausted...

In fact, when *pot* detects there is a small amount of data left in queue, it sends next batch request in background, hoping to fill the data until you exhaust the queue. And it works.

*Note:* **littlepot** sends next batch request, when there are less than 10 elements left in queue. Set `:cap` key when creating the pot to override this behaviour.

``` clojure
(def pot (make-pot get-batch :cap 25))
```

## Arg Batch Function

Some APIs require paging for the results.
Often this is done by providing `?page=` query param.

``` clojure
(def api-endpoint "http://image.provider.com/api/random?tag=cat&page=")
```

In this cases, our batch function accept one argument, results page:

``` clojure
(def get-batch [page]
  (-> api-endpoint
      (str page)
      http/get
      json/parse))
```

For this API `get-batch` is arg function, and we need also to define initial arguments using `:args` and function that update arguments for the next calls.

``` clojure
(def pot (make-pot get-batch
                   :args [1]
                   :next-args-fn (fn [[v]] [(inc v)])))
```

This way instead of random batches we request sequentially following endpoints

``` clojure
;; "http://image.provider.com/api/random?tag=cat&page=1"
;; "http://image.provider.com/api/random?tag=cat&page=2"
;; "http://image.provider.com/api/random?tag=cat&page=3"
```

When some exception occurs, or empty collection of data returned, cache considered exhausted and all subsequent calls return `:exhausted` marker.
