### Last price service
Service created as java API library according to this requiremenents:
```text
Business requirements:
 

Your task is to design and implement a service for keeping track of the last price for financial instruments.
Producers will use the service to publish prices and consumers will use it to obtain them.


The price data consists of records with the following fields:
* id: a string field to indicate which instrument this price refers to.
* asOf: a date time field to indicate when the price was determined.
* payload: the price data itself, which is a flexible data structure.

 

Producers should be able to provide prices in batch runs.
The sequence of uploading a batch run is as follows:
1. The producer indicates that a batch run is started
2. The producer uploads the records in the batch run in multiple chunks of 1000 records.
3. The producer completes or cancels the batch run.


Consumers can request the last price record for a given id.
The last value is determined by the asOf time, as set by the producer.
On completion, all prices in a batch run should be made available at the same time.
Batch runs which are cancelled can be discarded.

The service should be resilient against producers which call the service methods in an incorrect order,
or clients which call the service while a batch is being processed.

Technical requirements:

* Provide a working Java application which implements the business requirements
* The service interface should be defined as a Java API, so consumers and producers can be assumed to run in the same JVM.
* For the purpose of the exercise, we are looking for an in-memory solution (rather than one that relies on a database).
* Demonstrate the functionality of the application through unit tests
* You can use any open source libraries. Please include gradle or maven project definitions for the dependencies.
``` 

### Solution description

#### Showcase.
Please check [Show case](src/test/java/com/alugovoy/finance/storage/inmemory/StorageShowCase.java) unit test to figure 
out intended usage of the service.    

#### For producer
I have decided to create a dedicated `Batch` interface to represent a specific batch session operation. So to indicate 
a batch start producer need to execute method `BatchFactory.startBatch()` it will return a batch object which has 
specific methods for uploading and indication of cancel/complete operations.

In case when methods are executed in wrong order an `IllegalStateException` would be thrown.

Here is what is going on when different methods are executed:
1. `InMemoryStorage.startBatch()` would create an `Update` object with status **IN_PROGRESS** and empty collection of 
participating price item IDs.
2. `InMemoryStorage.InMemoryBatch.upload(chunk)` would go through all items in the chunk and add a modification for the 
data wrapper holding latest state. If there are no existing wrapper for the specified ID it would be created then. Each 
modification has a reference to the common status of the ongoing update/batch.
3. `InMemoryStorage.InMemoryBatch.complete()` transits common status for all modifications to **COMPLETED** and submits 
a task for clearing modifications inside wrappers and to remove `Update` object from the collection of in progress batches.
4. `InMemoryStorage.InMemoryBatch.cancel()` same as above but status becomes **CANCELED**

#### For consumer
There is an interface `Storage` with single method `getLatest(id)`. On executing this method a wrapper would be retrieved.
Wrapper keeps inside latest published details as well as ongoing modifications. When a method `DataWrappet.getLatest()` 
is executed then it goes through all existing modifications and merges current data with published. Once it is merged 
modification is removed from the wrapper. 

Thus, wrapper cleans from completed modifications from 2 directions:
1.  On `getLatest` reads. So each read would update wrapper with most recent data.
2.  Through `updateCompleted` which is executed from CleaningService.       
3.  Through `updateCanceled` which is executed from CleaningService. 

#### Strategy to ensure published data is available at the same time
In order to comply with this requirement I've decided to use a status object which is a wrapper of `AtomicInteger`. 
All modifications keep reference to the status of the batch they belong. So on `getLatest` operation modifications keep 
the latest information about their statuses. 

#### Thread safety
To provide thread safety for cases when several batches can run in parallel I am using several instruments.
1.  Inside `InMemoryStorage` I use `ConcurrentHashMap`'s atomic `compute` method.
2.  Inside `DataWrapper` I am using non-blocking algorithm to determine the latest published information. I achieve it 
through:
    1. A `version` AtomicInteger field. It increments on any change happening to the wrapper.
    2. `data` AtomicReference field. Through CAS operations it ensures that data will not be overridden without merge.
    3. `modifications` AtomicReferenceArray field. It also utilizes CAS operations on remove and add actions. 

#### Cleaning service responsibility
You can think about it like a garbage collection tool. It will run asynchronously and would update completed and canceled 
modifications one by one. Main use case I see for this tool is when we have not so hot DataWrappers so that finished 
modifications there not removed through `getLatest` method.

### Performance
You can find **JMH** based benchmarks in [benchmark](src/test/java/com/alugovoy/finance/storage/inmemory/benchmarks) folder. 
It covers separately `DataWrapper` and `InMemoryStorage` classes.

For `DataWrapper` run shows me this results in avg (nanoseconds per operation) mode:
```text
 Benchmark                                                     (DELAY_BETWEEN_UPDATES)  (MAX_PARALLEL_UPDATES)  Mode  Cnt     Score      Error  Units
 DataWrapperBenchmark.parallel                                                     100                       8  avgt    5   496.074 ±  817.981  ns/op
 DataWrapperBenchmark.parallel:getLatestWithUpdatesInParallel                      100                       8  avgt    5     8.587 ±    1.641  ns/op
 DataWrapperBenchmark.parallel:getLatestWithoutUpdates                             100                       8  avgt    5     9.122 ±    3.685  ns/op
 DataWrapperBenchmark.parallel:updateInProgress                                    100                       8  avgt    5  3906.342 ± 6553.520  ns/op
 DataWrapperBenchmark.parallel                                                      10                       8  avgt    5   162.518 ±  115.732  ns/op
 DataWrapperBenchmark.parallel:getLatestWithUpdatesInParallel                       10                       8  avgt    5     9.104 ±    2.651  ns/op
 DataWrapperBenchmark.parallel:getLatestWithoutUpdates                              10                       8  avgt    5     9.043 ±    3.955  ns/op
 DataWrapperBenchmark.parallel:updateInProgress                                     10                       8  avgt    5  1236.657 ±  924.291  ns/op
 DataWrapperBenchmark.parallel                                                       0                       8  avgt    5    15.758 ±    2.032  ns/op
 DataWrapperBenchmark.parallel:getLatestWithUpdatesInParallel                        0                       8  avgt    5    10.453 ±    0.823  ns/op
 DataWrapperBenchmark.parallel:getLatestWithoutUpdates                               0                       8  avgt    5    10.554 ±    1.529  ns/op
 DataWrapperBenchmark.parallel:updateInProgress                                      0                       8  avgt    5    52.489 ±   10.325  ns/op
```                  
I did use 2 different parameters to for the runs:
 1.  Parallel updates at the same time
 2.  Delay between updates     
 I've added delay between updates artificially. This has caused screwing of the  `updateInProgress` results for cases when 
 delay was high.
 
 For `InMemoryStorage` run shows me this results in avg (nanoseconds per operation) mode:
 ```text
 Benchmark                                                         (SIZE)  Mode  Cnt        Score        Error  Units
 InMemoryStorageBenchmark.getLatestWithoutParallelUpdated         1000000  avgt    5      578.589 ±     14.930  ns/op
 InMemoryStorageBenchmark.getLatestWithoutParallelUpdated          100000  avgt    5      445.980 ±     23.089  ns/op
 InMemoryStorageBenchmark.getLatestWithoutParallelUpdated           10000  avgt    5      152.892 ±     89.405  ns/op
 InMemoryStorageBenchmark.parallel                                1000000  avgt    5   253331.487 ± 103734.831  ns/op
 InMemoryStorageBenchmark.parallel:getLatest                      1000000  avgt    5     1503.328 ±    319.491  ns/op
 InMemoryStorageBenchmark.parallel:uploadBatch                    1000000  avgt    5  2016128.604 ± 828586.999  ns/op
 InMemoryStorageBenchmark.parallel                                 100000  avgt    5   147512.463 ±  16621.843  ns/op
 InMemoryStorageBenchmark.parallel:getLatest                       100000  avgt    5      662.027 ±     72.414  ns/op
 InMemoryStorageBenchmark.parallel:uploadBatch                     100000  avgt    5  1175465.515 ± 132471.110  ns/op
 InMemoryStorageBenchmark.parallel                                  10000  avgt    5    71885.219 ±   3232.486  ns/op
 InMemoryStorageBenchmark.parallel:getLatest                        10000  avgt    5      269.708 ±     45.554  ns/op
 InMemoryStorageBenchmark.parallel:uploadBatch                      10000  avgt    5   573193.798 ±  25923.420  ns/op
```
Here I've used only one parameter **SIZE** which represented size of unique IDs for the price details.

It has shown that with grow of the size latency has been growing too. I assume it is a result of IDs collision inside 
main `ConcurrentHashMap`.

### Flaws and possible improvements

1. **Modifications limit**. At the moment current implementation has strong limitation for a 100 parallel modifications. 
I've added *TODO* for the most obvious improvement that can be done. It is to merge modifications coming from the same 
batch and leave there only latest one. In general this 100 can become configuration parameter,
2. **Add batch timeout**. It makes sense to add a timeout for batch sessions to evict (cancel) them on reaching it.
3. **Move cleaningPool to CleaningService**. From OOD perspective it makes more sense to have cleaning tasks thread pool
inside cleaning service itself. Because configuration of the pool couples with disruptor configuration inside cleaning 
service implementation.
4. **Add unit tests for shutdown**. Shutdown procedure must be thoroughly covered by unit tests.
5. **Make performance less dependent on the storage size**. Benchmarks of the `DataWrapper` shows that it is quite 
performant. And it looks like a lot of time is wasted at the level of the `InMemoryStorage`. If I'd have more time I would 
investigate the real reason behind that.                