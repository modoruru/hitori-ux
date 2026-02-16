# ux
This repo contains UX (user experience) module for [hitori](https://github.com/modoruru/hitori-ux) framework
## Main features
- Storage for additional user data (+ default local implementation)
- Custom name tags via TextDisplay's
- Chat processing
    - Global & local chat (+ api to add another channels)
    - Direct messages
    - Player ignoring
    - Mentions, URL processing, Style formatting and Replacements (placeholders for players to use in chat, e. g. deaths, playtime, ping)
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
You may need to use Storage or other API's of this module.\
So, this module is published via [JitPack](https://jitpack.io/)

Latest version: [![](https://jitpack.io/v/modoruru/hitori-ux.svg)](https://jitpack.io/#modoruru/hitori-ux)

<details>
<summary>maven</summary>

```xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>
```

```xml
	<dependency>
	    <groupId>com.github.modoruru</groupId>
	    <artifactId>hitori-ux</artifactId>
	    <version>version</version>
	</dependency>
```
</details>
<details>
<summary>gradle</summary>

```groovy
repositories {
    // ...
    maven { url 'https://jitpack.io' }
}
```

```groovy
dependencies {
    // ...
    implementation 'com.github.modoruru:hitori-ux:version'
}
```
</details>
