# web-index

Locale-aware free-text search over auction items for the Bruun Rasmussen website, built on Lucene 3.6.

## Usage

```xml
<dependency>
  <groupId>dk.es.br</groupId>
  <artifactId>web-index</artifactId>
  <version>0.17</version>
</dependency>
```

```java
LuceneIndex index = LuceneIndex.DISK(new Locale("da"), new File("/var/index/da"));

LuceneIndex.Writer w = index.create();   // or open() to append
w.newDocument(300692980L)
    .textField("description", "Mogens Balle: \"Rød Begyndelse\". Olie på lærred.")
    .textField("auctionCatalogNumber", "1907/622")
    .build();
w.close();

Set<Long> itemIds = index.search("rød begyndelse", "description");
index.deleteItem(300692980L);
```

Use `LuceneIndex.RAM(locale)` for an in-memory index. Searches combine terms with AND and return at most 1000 item ids. A query that fails is logged and returns an empty set.

## Text processing

Indexed text and queries are normalized the same way. Accents are stripped and model numbers are split, so `PK22`, `pk-22` and `PK 22` all match. Text is then tokenized, lowercased, stop-word filtered and stemmed with Snowball. Stemmers exist for Danish, Swedish and English.

## Word lists

The library ships no word lists. The application must put these files on its classpath for each language it uses. All are optional.

| Resource                           | Format                     | Purpose                                                       |
|------------------------------------|----------------------------|---------------------------------------------------------------|
| `lucene/stopwords_<lang>.txt`      | Snowball (`\|` comments)   | Words left out of the index                                   |
| `lucene/exceptions_<lang>.txt`     | `.properties`, UTF-8       | Replace a word before stemming, e.g. `bøger=bog`              |
| `lucene/generalizations_<lang>.txt`| `.properties`, UTF-8       | Also index a more general term, e.g. `konferencestol=stol`    |

Generalizations apply at index time only, after stemming. A search for `stol` therefore also finds items described as `konferencestole`. Examples are in `src/test/resources/lucene/`.

## Building

```sh
mvn test
mvn package
```

The project needs Java 8 or later. Releases are made with `mvn release:prepare release:perform`.
