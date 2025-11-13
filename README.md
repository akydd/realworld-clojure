# realworld-clojure

A Clojure implementation of the [RealWorld server API](https://docs.realworld.show/specifications/backend/introduction/).


## Prerequisites

1. A working java installation
2. Leiningen
3. Docker compose


## Installation

1. Clone this repo.
2. Run `docker compose up` to install preconfigured Postgres instances, one for "production" and the other for integration tests.


## Architecture

The source code  is organized into ports and adapters (hexagonal), using the
[Component](https://github.com/stuartsierra/component) library to manage
the components and dependency injection.

The only automated tests are integration tests, located in
`test/realworld_clojure/integration`. Each integration test starts up
a separate instance of the app that runs against a dedicated db instance for
integration tests.

For development, you can start and manage the application from within the REPL.

To start the app, switch to the `user` namespace and run `go`:

```
$ lein repl
...
nREPL server started on port 52745 on host 127.0.0.1 - nrepl://127.0.0.1:52745
REPL-y 0.5.1, nREPL 1.0.0
Clojure 1.11.3
OpenJDK 64-Bit Server VM 25.0.1
    Docs: (doc function-name-here)
          (find-doc "part-of-name-here")
  Source: (source function-name-here)
 Javadoc: (javadoc java-object-or-class-here)
    Exit: Control+D or (exit) or (quit)
 Results: Stored in vars *1, *2, *3, an exception in *e
...
realworld-clojure.core=> (in-ns 'user)
#object[clojure.lang.Namespace 0x1b9e9be9 "user"]
user=> (go)
Starting database with {:dbtype postgresql, :host localhost, :port 8091, :dbname app, :user admin, :password password}
Starting webserver on port 8090
#<SystemMap>
user=> 
```

Note that this runs the app on the same port and against the same db as when
starting the app from outside the repl (see the Usage section below), so you
cannot run the app in both ways at the same time.

Call `reset` if you make changes to the code and need to restart the app:
```
user=> (reset)
Stopping webserver
Stopping database
:reloading <truncated output>
Starting database with {:dbtype postgresql, :host localhost, :port 8091, :dbname app, :user admin, :password password}
Starting webserver on port 8090
#<SystemMap>
user=>
```

You can insepct the application's system map:
```
user=> (get-in system [:database :dbspec])
{:dbtype "postgresql", :host "localhost", :port 8091, :dbname "app", :user "admin", :password "password"}
```

You can execute any public functions:
```
user=> (realworld-clojure.adapters.db/get-article-by-slug (:database system) "test")
```

Call `stop` to stop the app:
```clojure-repl
user=> (stop)
Stopping webserver
Stopping database
#<SystemMap>
user=>
```


## Usage

Run it with Leiningen:
```sh
$ lein run
```

Or create and run the jar file:
```sh
$ lein uberjar
... output skipped ...
$ java -jar target/uberjar/realworld-clojure-0.1.0-SNAPSHOT-standalone.jar [args]
```


## Options

See `realworld-clojure/resources/config.edn` and `realworld-clojure/resources/test-config.edn`.

The production instance of the app can use the environment variables:
```
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
PORT
JWT_SECRET
```

The integration test instance of the app can use the environment variables:
```
TEST_DB_HOST
TEST_DB_PORT
TEST_DB_NAME
TEST_DB_USER
TEST_DB_PASSWORD
TEST_PORT
TEST_JWT_SECRET
```

In the absence of the above environment vars, the app uses default values that
match the values in the docker compose file (`compose.yaml`).

If you intend to change any of the database options, ensure that the new values
are first also applied to the docker compose file, and then rerun `docker
up` as needed.

## Examples

...

## Design Decisions



### Timestamps
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
