このチュートリアルはさまざまな[Akka Cluster](https://doc.akka.io/docs/akka/2.6/typed/cluster.html)の機能を示す3つのサンプルが含まれています。

- Clusterメンバーシップイベントのサブスクライブ
- Clusterノード上で稼働中のアクターにメッセージを送る
- クラスター対応ルーター

# シンプルなCluster例

[application.conf](./src/main/resources/application.conf) を確認します。

AkkaのプロジェクトでCluster機能を有効にするには、少なくともリモート設定を追加し、Clusterを `akka.actor.provider` として使用する必要があります。
`akka.cluster.seed-nodes` は、 通常、`application.conf` ファイルにも追加する必要があります。

シードノードは、新しく開始されたノードがClusterに参加するために接続しようとする構成済みの接点です。

別のマシンでノードを起動する場合は、`127.0.0.1` ではなく、 `application.conf` でマシンのIPアドレスまたはホスト名を指定する必要があることに注意してください。

[Main.scala](./src/main/scala/sample/cluster/simple/Main.scala) を確認します。

小さなプログラムとその構成により、Clusterが有効な状態でActorSystemが起動します。
Clusterに参加し、いくつかのメンバーシップイベントを記録するアクターを開始します。
[ClusterListener.scala](./src/main/scala/sample/cluster/simple/ClusterListener.scala)アクターを見てください。

Clusterの概念の詳細については、[documentation](https://doc.akka.io/docs/akka/2.6/typed/cluster.html)をご覧ください。

このサンプルを実行するには、 `sbt "runMain sample.cluster.simple.Main` と入力してください。

`sample.cluster.simple.Main`は、3つのActorシステム(Clusterメンバー)を同じJVMプロセス上で開始します。
それらを別々のプロセスで実行する方が興味深い場合があります。
アプリケーションを止めて3つのターミナルウィンドウを開きましょう。

1つ目のターミナルで、以下のコマンドを実行し1つ目のシードノードを起動します。

```bash
$ sbt "runMain sample.cluster.simple.Main 25251"
```

25251ポートは構成内の最初のシードノード要素に対応します。
ログ出力に、Clusterノードが開始され、ステータスが「Up」に変更されたことが分かります。

2つ目のターミナルで、以下のコマンドを実行し2つ目のシードノードを起動します。

```bash
$ sbt "runMain sample.cluster.simple.Main 25252"
```

25252ポートは構成内の2つ目のシードノード要素に対応します。
ログ出力に、Clusterノードが開始され、他のシードノードに参加しClusterのメンバーになります。
ステータスが「Up」に変更されます。

1つ目のターミナルに切り替えてログの出力を見るとメンバーに参加したことが見てとれます。

3つ目のターミナルで以下のコマンドを実行し別のノードを開始します。

```bash
$ sbt "runMain sample.cluster.simple.Main 0"
```

ここで、ポート番号を指定する必要はありません。0は、ランダムに利用可能なポートを使用することを意味します。
構成済みシードノードの1つに参加します。
他のターミナルのログ出力を確認します。

必要に応じて、同じ方法でさらに多くのノードを起動します。

任意のターミナルで、'ctrl-c'を押下するとノードの1つがシャットダウンします。
この操作により、ノードはクラスターから適切な脱退を行い、クラスター内の他のノードに脱退することを通知します
その後、クラスターから削除され、他の端末のログ出力で確認できます。

Actorのソースコードを再度確認します。
特定のクラスターイベントのサブスクライバーとして自身を登録しています。
現在の状態に至るまでの一連のイベントが通知されています。
その後、クラスターで発生した変更のイベントを受け取っています。

これで、クラスターメンバーシップイベントをサブスクライブする方法を確認しました。
もっと知りたい場合は、 [documentation](https://doc.akka.io/docs/akka/2.6/typed/cluster.html#cluster-subscriptions)を読んでください。
メンバーシップイベントは、クラスターの状態を見れますが、クラスターの他のノード上のActorへのアクセスには役立ちません。
他のノードのActorにアクセスしたい場合は、[Receptionist](https://doc.akka.io/docs/akka/2.6/typed/actor-discovery.html#receptionist)を使います。

# Worker登録の例

`Receptionist`は、クラスターを使用していない単一のJVMアプリとクラスターアプリの両方で機能するサービスレジストリです。
`ActorRef`は、`ServiceKey`を使用してreceptionistに登録されます。
サービスキーは、登録されたアクターが受け入れるメッセージのタイプと文字列識別子で定義されます。

ここでは、バックエンドの役割を持つノードでのみワーカーがreceptionistに自分を登録して、フロントエンドノードが作業を実行できるワーカーを認識できるようにする方法を示す例を見てみましょう。
ノードの役割はセットであるため、ノードは両方の役割を持つ可能性があることに注意してください。
ただし、提供されるメインでは1つの役割しか許可されません。

サンプルアプリケーションは、テキストを変換するサービスを提供します。
定期的な間隔で、フロントエンドはテキストを処理するための外部リクエストをシミュレートし、テキストがある場合は、利用可能なワーカーに転送します。

ワーカーの検出は動的であるため、`backend`ノードと`frontend`ノードの両方をクラスターに動的に追加できます。

変換ジョブを実行するバックエンドワーカーは、[TransformationBackend.scala](./src/main/scala/sample/cluster/transformation/Worker.scala) で定義されています。
ワーカーを起動すると、ワーカーがreceptionistに登録され、クラスター内の任意のノードの`ServiceKey`を通じてワーカーを検出できるようになります。

ユーザーのジョブをシミュレートし、利用可能なワーカーを追跡するフロントエンドは、[Frontend.scala](./src/main/scala/sample/cluster/transformation/Frontend.scala)で定義されています。
アクターは、 `WorkerServiceKey`を使用して `Receptionist`にサブスクライブし、クラスター内の使用可能なワーカーのセットが変更されたときに更新を受け取ります。
ワーカーが死亡するか、そのノードがクラスターから削除された場合、フロントエンドはワーカーを監視する必要が無いため、receptionistが更新されたリストを送信します。

このサンプルを実行するには、以前に開始したクラスターサンプルをすべてシャットダウンしてから `sbt runMain sample.cluster.transformation.Main` と入力してください。

TransformationApp は、5つのActorシステム(Clusterメンバー)を同じJVMプロセス上で開始します。
それらを別々のプロセスで実行する方が興味深い場合があります。
アプリケーションを止めて3つのターミナルウィンドウを開きましょう。

```bash
# terminal1
$ sbt "runMain sample.cluster.transformation.Main backend 25251"
# terminal2
$ sbt "runMain sample.cluster.transformation.Main backend 25252"
# terminal3
$ sbt "runMain sample.cluster.transformation.Main backend 0"
# terminal4
$ sbt "runMain sample.cluster.transformation.Main frontend 0"
# terminal5
$ sbt "runMain sample.cluster.transformation.Main frontend 0"
```
Akkaに組み込まれているコンポーネントには、receptionistにサブスクライブし、利用可能なアクターを追跡して、そのようなやり取りを大幅に簡素化するコンポーネントがあります。
それがグループルーターです。 
次のセクションでそれらをどのように使用できるかを見てみましょう！

# クラスター対応ルーター

[グループルーター](https://doc.akka.io/docs/akka/2.6/typed/routers.html#group-router)は`Receptionist`に依存しているため、クラスターの任意のノードに登録されているサービスにメッセージをルーティングします。

クラスター対応ルーターを利用するいくつかのサンプルを見てみましょう。

# クラスタールーティングの例

ルーターを使用してクラスター全体に作業を分散する2つの異なる方法を見てみましょう。

サンプルはAkkaクラスターのさまざまな部分を示しているだけで、復元力のある分散アプリケーションを構築するための完全な構造を提供していないことに注意してください。
[Distributed Workers With Akka](https://developer.lightbend.com/guides/akka-distributed-workers-scala/)サンプルは、回復力のある分散処理アプリケーションを構築するために解決する必要のある問題の多くをカバーしています。

## ルートのグループの例

サンプルアプリケーションは、テキストの統計を計算するサービスを提供します。
一部のテキストがサービスに送信されると、テキストが単語に分割され、各単語の文字数をカウントするタスクが、ルーターのルートである別のワーカーに委任されます。
各単語の文字数は、すべての結果が収集されたときに単語ごとの平均文字数を計算するアグリゲーターに送り返されます。

各単語の文字数をカウントするワーカーは、[StatsWorker.scala](./src/main/scala/sample/cluster/stats/StatsWorker.scala)で定義されています。

ユーザーからテキストを受け取り、それを単語に分割し、ワーカーのプールに委任し、結果を集計するサービスは、[StatsService.scala](./src/main/scala/sample/cluster/stats/StatsService.scala)で定義されています。

これまでのところ、クラスタ固有のものはなく、単純なActorだけであることに注意してください。

クラスター内のノードは、さまざまなタスクを実行するために役割でマークを付けることができます。
この例では、単語統計の処理を行う必要があるクラスターノードを指定する役割として`compute`を使用します。

[StatsSample.scala](./src/main/scala/sample/cluster/stats/Main.scala)では、各`compute`のノードがN個のローカル`StatsWorkers`に作業を分散する`StatsService`を開始します。
次に、クライアントノードは、`group`ルーターを介して`StatsService`インスタンスにメッセージを送信します。
ルーターは、クラスター受付とサービスキーにサブスクライブすることでサービスを見つけます。
各ワーカーは、起動時にreceptionistに登録されます。

この設計では、単一の`compute`ノードがクラッシュしても、そのノードで進行中の作業が失われるだけで、他のノードは作業を続行できますが、進行中の現在の作業のリストを要求する場所は1つだけではありません。

まだ開始されていない場合、サンプルを実行するには、 `sbt runMain sample.cluster.stats.Main` と入力します。

StatsSampleは、同じJVMプロセスで4つのActorシステム(Clusterメンバー)を起動します。
それらを別々のプロセスで実行する方が興味深い場合があります。
アプリケーションを停止し、次のコマンドを別のターミナルウィンドウで実行します。

```bash
# terminal1
sbt "runMain sample.cluster.stats.Main compute 25251"
# terminal2
sbt "runMain sample.cluster.stats.Main compute 25252"
# terminal3
sbt "runMain sample.cluster.stats.Main compute 0"
# terminal4
sbt "runMain sample.cluster.stats.Main client 0"
```

# Cluster Singletonでのルーターの例

[StatsSampleOneMaster.scala](./src/main/scala/sample/cluster/stats/AppOneMaster.scala)の各`compute`ノードはN個のワーカーを開始し、receptionistに登録します。
`StatsService`は、Akka Cluster Singletonを介してクラスター内の単一のインスタンスで実行されます。
ただし、実際の作業はすべての計算ノードのワーカーによって実行されます。
ワーカーは、シングルトンが使用するグループルーターを介して到達されます。

この設計により、シングルトンに現在の作業を照会することが可能になります。
現在進行中のすべての要求を認識しており、現在進行中の作業を正確に知ることに基づいて判断を下す可能性があります。

ただし、シングルトンノードがクラッシュした場合、シングルトンの状態は永続的ではないため、新しいノードで開始すると、StatsServiceは以前の作業を認識しないため、進行中の作業はすべて失われます。
他のノードのいずれかがクラッシュした場合、それらに送信された進行中の作業のみが失われますが、進行中の各リクエストは異なるノードの複数の異なるワーカーによって処理されるため、クラッシュにより多くのリクエストに問題が発生する可能性があります。

まだ開始されていない場合、サンプルを実行するには、 `sbt runMain sample.cluster.stats.AppOneMaster` と入力します。

StatsSampleOneMasterは、同じJVMプロセスで4つのActorシステム(Clusterメンバー)を起動します。
それらを別々のプロセスで実行する方が興味深い場合があります。
アプリケーションを停止し、次のコマンドを別のターミナルウィンドウで実行します。

```bash
# terminal1
sbt "runMain sample.cluster.stats.AppOneMaster compute 25251"
# terminal2
sbt "runMain sample.cluster.stats.AppOneMaster compute 25252"
# terminal3
sbt "runMain sample.cluster.stats.AppOneMaster compute 0"
# terminal4
sbt "runMain sample.cluster.stats.AppOneMaster client 0"
```

# テスト

テストは、[src/multi-jvm](./src/multi-jvm) にあります。
`sbt multi-jvm/test`と入力すると実行することができます。
