このチュートリアルには、
[Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html) 、
[Akka Cluster Singleton](https://doc.akka.io/docs/akka/2.6/typed/cluster-singleton.html) 、
[Akka Persistence](https://doc.akka.io/docs/akka/2.6/typed/persistence.html) 、
[Akka Persistence Query](https://doc.akka.io/docs/akka/2.6/persistence-query.html)
を使用したCQRS設計を示すサンプルが含まれています。

# 概要

このサンプルアプリケーションは、CQRS+ES設計を実装しています。
CQRS+ES設計は、書き込みモデルによってCassandraに永続化された選択されたイベントの読み取りモデルに副作用があります。
このサンプルでは、副作用はラインを記録しています。
より実用的な例は、Kafkaトピックにメッセージを送信するか、リレーショナルデータベースを更新することです。

# Write Model

Writeモデルはショッピングカートです。

実装はシャーディングされたアクターに基づいています。
各`ShoppingCart`は [Akka Cluster Sharding](https://doc.akka.io/docs/akka/2.6/typed/cluster-sharding.html) エンティティです。
エンティティアクター `ShoppingCart` は [EventSourcedBehavior](https://doc.akka.io/docs/akka/2.6/typed/persistence.html) です。

ショッピングカートからのイベントにはタグが付けられ、読み取りモデルによって消費されます。

# Read Model

読み取りモデルは、「ロード」が複数のプロセッサに分散されるように実装されます。
その数は、 `event-proseccor.parallelism` です。
[Sharded Daemon Process](https://doc.akka.io/docs/akka/current/typed/cluster-sharded-daemon-process.html) を使用して実装されます。

# サンプルコードの実行

1. Casandraサーバーを実行する

```bash
$ sbt "runMain sample.cqrs.Main cassandra"
```

2. Writeモデルを実行するノードを開始する

```bash
sbt -Dakka.cluster.roles.0=write-model "runMain sample.cqrs.Main 2551"
```

3. Readモデルを実行するノードを開始する

```bash
sbt -Dakka.cluster.roles.0=read-model "runMain sample.cqrs.Main 2552"
```

4. rolesとportを定義することで、より多くの書き込みまたは読み取りノードを開始できます。

```bash
sbt -Dakka.cluster.roles.0=write-model "runMain sample.cqrs.Main 2553"
sbt -Dakka.cluster.roles.0=read-model "runMain sample.cqrs.Main 2554"
```

curlを実行してみましょう。

```bash
# add item to cart
curl -X POST -H "Content-Type: application/json" -d '{"cartId":"cart1", "itemId":"socks", "quantity":3}' http://127.0.0.1:8051/shopping/carts

# get cart
curl http://127.0.0.1:8051/shopping/carts/cart1

# update quantity of item
curl -X PUT -H "Content-Type: application/json" -d '{"cartId":"cart1", "itemId":"socks", "quantity":5}' http://127.0.0.1:8051/shopping/carts

# check out cart
curl -X POST -H "Content-Type: application/json" -d '{}' http://127.0.0.1:8051/shopping/carts/cart1/checkout
```

もしくは、同じ `curl` コマンドをポート8052に向けてください。
