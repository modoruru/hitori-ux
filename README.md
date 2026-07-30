# ux
[Русская версия](./README_ru.md) | [Contributing](https://github.com/modoruru/.github/blob/main/CONTRIBUTING.md)

This repo contains UX (user experience) module for [hitori](https://github.com/modoruru/hitori) framework
## Main features
- Storage for additional user data (+ default local implementation)
- Custom name tags via TextDisplay's
- Chat processing
    - Global & local chat (+ api to add another channels)
    - Direct messages
    - Player ignoring
    - Mentions, URL processing, Style formatting and Replacements (placeholders for players to use in chat, e.g. deaths, playtime, ping)
- Tab processing
    - Header, footer
    - Player tab name
    - Objective's
- Events & Streams announcements

## Usage
You can get a jar from [Actions](https://github.com/modoruru/hitori-ux/actions) tab. Module is built after almost every commit.\
Also, you can get module from [Releases](https://github.com/modoruru/hitori-ux/releases) (if there's any).

After downloading the jar, just put it into hitori folder. Then restart the server.

## API
You may need to use Storage or other API's of this module.

<details>
<summary>maven</summary>

```xml
<repository>
  <id>modoru-releases</id>
  <name>modoru repository</name>
  <url>https://repository.modoru.fun/releases</url>
</repository>
```

```xml
<dependency>
  <groupId>su.hitori.ux</groupId>
  <artifactId>module</artifactId>
  <version>1.2.2</version>
</dependency>
```
</details>
<details>
<summary>gradle</summary>

```groovy
maven {
  name = "modoruReleases"
  url = uri("https://repository.modoru.fun/releases")
}
```

```groovy
dependencies {
  // ...
  implementation 'su.hitori.ux:module:1.2.2'
}
```
</details>
