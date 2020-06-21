(ns dv.pathom-wp.client.pathom-parser
  (:require
    [com.wsscode.pathom.connect :as pc]
    [com.wsscode.pathom.core :as p]))

(defn parser [resolvers]
  (p/parallel-parser
    {::p/env     {::p/reader               [p/map-reader
                                            pc/parallel-reader
                                            pc/open-ident-reader
                                            p/env-placeholder-reader]
                  ::p/placeholder-prefixes #{">"}
                  ::pc/mutation-join-globals [:app/id-remaps]
                  ;::db                       (db/setup-db db-settings)
                  }
     ::p/mutate  pc/mutate-async
     ::p/plugins [(pc/connect-plugin {::pc/register resolvers})
                  p/error-handler-plugin
                  p/trace-plugin]}))
