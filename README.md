# Cachier

**Cachier** is a tiny library devoted to transform batched data requests into single method to obtain single element.

## Rationale

Most of APIs return batched data (_like 50 elements in one response_), but in a lot of cases you need just single element access. General solution to this problem is to send request for batch, get the data, save it somewhere in cache, get the next element from cache, if cache is exhausted send next request, wait, etc. Cachier solves some of these problems.

**Storage** It is backed by `clojure.lang.PersistentQueue`, clojure queue implementation, so you don't need to care about efficient storage.

**Autofill** It sends request for next batch in a background, when your cached data is close to exhaustion, so the process of cache filling goes automatically and silently. 

**Non-blocking** You do not need to wait when data appeares in cache; if something there, return it, if not, return `:no-data`

**Composable** Having function to retrieve single element `(get-one)` you can easily get fifty elements by calling `(take 50 (repeatedly get-one))`.

**Concurrency** It encapsulates whole state in `atom`, so multiple consumers allowed.

## Usage

Add dependency

_IN PROGRESS_

``` clojure
[com.mishadoff/cachier "0.1.0"]
```

Include it in your namespace

``` clojure
(:require [cachier :as c])
```

Create cache

``` clojure
(def cache
  (c/make-cache (fn []
                  ;; simulate latency
                  (Thread/sleep (rand-int 1000)
				  ;; return data
                  (range 10)))))
```

Get from cache

``` clojure
(c/hit cache)
```

## Future Plans

Occasionally, some features from [TODO.md](doc/todo.md) will be added.

## License

Copyright © 2015

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
