# realworld-clojure

Implementation of the [RealWorld server API](https://docs.realworld.show/specifications/backend/introduction/).

## Installation

1. Clone this repo.
2. 

## Usage

FIXME: explanation

    $ java -jar realworld-clojure-0.1.0-standalone.jar [args]

## Options

All options for the app are set in ...


## Examples

...

## Design Decisions
The spec specifies that all returned timestamps are formatted as
`2016-02-18T03:22:56.637Z`.

The jdbc driver, db, and json converter have to work together so that timestamps
from the db are returned in utc, and not formatted for the local time zone. This
is accomplished by:

1. Using table column type `timestamptz` to store dates.
2. Setting the jdbc driver to return `timestamptz` values as `java.time.Instant` values.
```clojure
(ns ...
   (:require ...
             [next.jdbc.date-time :as dt]
             ...))

(dt/read-as-instant)
```
3. Tweaking the json conversion. By default the ring `wrap-json-response`
middleware formats timestamps with the nulliseconds truncated, like
`2016-02-18T03:22:56Z`. Examining the source code revealed that the code called
by the middleware, `json/generate-string`, also accepts a `:date-format` option.
Passing this options to the middleware resulted in the desired format:
```clojure
;; `'Z'` is used instead of `Z` since the timestamps returned are already utc.
(wrap-json-response {:date-format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"})
```

## License

Copyright © 2025

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
