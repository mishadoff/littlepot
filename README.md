# littlepot [![Build Status](https://travis-ci.org/mishadoff/littlepot.svg?branch=master)](https://travis-ci.org/mishadoff/littlepot) [![Coverage Status](https://coveralls.io/repos/mishadoff/littlepot/badge.svg)](https://coveralls.io/r/mishadoff/littlepot)

> Cook, little pot, cook!
>
>  -- The Magic Porridge Pot, Brothers Grimm

**littlepot** is a tiny library devoted to transform batched data requests into single element request.

## Rationale

Most of public APIs return batched data (_like 50 elements in one response_), but in some cases you need just single element access. General solution to this problem is to send request for batch, get the data, save it somewhere in collection, get the next element from collection, send next request if collection is exhausted, wait, repeat.
**littlepot** solves some of these problems.

**Storage.** It is backed by `clojure.lang.PersistentQueue`, clojure queue implementation, so you don't need to care about efficient storage.

**Autofill.** It sends request for next batch in a background, when your cached data is close to exhaustion, so the process of filling cache goes automatically and silently.  

**Non-blocking.** You do not need to wait when data appears in cache; if something there, return it, if not, return `:no-data`.

**Composable.** Having function to retrieve single element `(get-one)` you can easily get fifty elements by calling `(take 50 (repeatedly get-one))`.

**Concurrency.** It encapsulates whole state in `ref` and uses `STM`, so multiple consumers allowed. Also, guaranteed that at most one batch will be in progress.

## Usage

Add dependency

``` clojure
[com.mishadoff/littlepot "0.1.1"]
```

Include it in your namespace

``` clojure
(:require [com.mishadoff/littlepot :refer :all])
```

Create pot

``` clojure
(def pot
  (make-pot (fn []
              ;; simulate latency
              (Thread/sleep (rand-int 1000)
	          ;; return data
              (range 10)))))
```

Get from pot

``` clojure
(cook pot) ;; => :no-data
;; wait 1 second
(cook pot) ;; => 0
```

For advanced usage and some real use cases,
go to [tutorial](doc/tutorial.md)

## Future Plans

Occasionally, some features from [TODO.md](doc/todo.md) will be added.

## License

Distributed under the [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html)
