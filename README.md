# realworld-clojure

Implementation of the realworld server API.

## Installation

Download from http://example.com/FIXME.

## Usage

FIXME: explanation

    $ java -jar realworld-clojure-0.1.0-standalone.jar [args]

## Options

FIXME: listing of options this app accepts.

## Examples

...

## Design Decisions
The spec specifies that all returned timestamps are formatted as `2016-02-18T03:22:56.637Z`.

Two things needed to happen for this to work.

### JDBC and DB
The jdbc driver and db had to work together so that timestamps from the db were returned in utc, and not formatted for the local time zone. This was accomplished with the following design decisions:

* use column type timestamptz to store dates
* set jdbc driver to return `java.time.Instant` values from the db:
```clojure
(ns ...
   (:require ...
             [next.jdbc.date-time :as dt]
             ...))

(dt/read-as-instant)
```

### JSON formatting
By default the ring `wrap-json-response` middleware formats timestamps with the nulliseconds
truncated, like `2016-02-18T03:22:56Z`. Examining the source code revealed that the code called
by the middleware, `json/generate-string`, also accepts a `:date-format` option:
```clojure
(wrap-json-response {:date-format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"})
```
Here `'Z'` was used instead of just `Z` since the timestamps returned were already utc.

## License

Copyright © 2024 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
