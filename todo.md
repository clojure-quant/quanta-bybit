

I have 3 projects: quanta-blotter and fix-engine and quanta-market
/home/florian/repo/clojure-quant/fix-engine
/home/florian/repo/clojure-quant/quanta-blotter
/home/florian/repo/clojure-quant/quanta-market


in quanta-market there is old code to get quotes and orders from bybit.

in this solution I want to be able to do orders and quotes from bybit.

you need to implement:

similar demo as in fix-engine/demo to get quotes and send orders


session handler similar to fix-engine.impl.fix-session
but instead of tcp socket you use websockt.
you do not request a security list.

asset mapper similar to fix-engine.impl.asset-converter
  the mapping is 
  "USDT.BB" -> {:symbol "BTCUSDT" :category :spot}
  "USDT.PF.BB" -> {:symbol "BTCUSDT" :category :perpetual}
  "USDT.LF.BB" -> {:symbol "BTCUSDT" :category :linear}
  "USDT.O.BB" -> {:symbol "BTCUSDT" :category :option}


similar to fix-engine.quote.account

just start with the quote part first.

account format for trade:
{:account/id 2000
 :account/api :bybit-trade
 :account/notes "rene1 bybit spot"
 :account/settings {:connection {:mode :test
                                 :segment :spot}
                    :login {:api-key "hubAsPmhBcLoRBwL0g"
                            :api-secret "rbH7DnkVXoRnsVRSQNOxpEieHusMbbtuZQLR"}}}

{:account/id 2001
 :account/api :bybit-trade
 :account/notes "rene1 bybit perpetual"
 :account/settings {:connection {:mode :test 
                                 :segment :perpetual}
                    :login {:api-key "hubAsPmhBcLoRBwL0g"
                            :api-secret "rbH7DnkVXoRnsVRSQNOxpEieHusMbbtuZQLR"}}}

account format for quote:
{:account/id 2002
 :account/api :bybit-quote
 :account/notes "bybit quotes spot"
 :account/settings {:connection {:mode :test
                                 :segment :spot}}}

{:account/id 2002
 :account/api :bybit-quote
 :account/notes "bybit quotes perpetual"
 :account/settings {:connection {:mode :test
                                 :segment :perpetual}}}

what is wrong with the old code:
- I have wrapped the 4 segments togeter into one, new one is 
- implements old api.


you need to implement
quanta.bybit.quote.messaging (similar to fix-engine.quote.messaging)
quanta.bybit.quote.account (similar to fix-engine.quote.account)
quanta.bybit.blotter.messaging (similar to fix-engine.blotter.messaging)
quanta.bybit.blotter.account (similar to fix-engine.blotter.account)
quanta.bybit.impl.asset-converter (similar to fix-engine.impl.asset-converter)
quanta.bybit.impl.session (similar to fix-engine.impl.fix-session)
quanta.bybit.impl.connect (similar to fix-engine.impl.connect)

quanta.bybit.impl.session
needs to connect to websocket, send login (if login is defined; it is not 
defined for quote connections) and send heartbeats/process-heartbeats.
what session does is it handels the json message encoding. so it reads
strings decodes json, and when writing it encodes in json.
