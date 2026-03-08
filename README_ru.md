# ux
[English version](./README.md)| [Контрибьютинг](https://github.com/modoruru/.github/blob/main/CONTRIBUTING_ru.md)

Этот репозиторий содержит UX (user experience, пользовательский опыт) модуль для [hitori](https://github.com/modoruru/hitori) фреймворка. 
## Основные особенности
- Хранилище для дополнительных пользователских данных (+ стандартная локальная реализация)
- Кастомные нейм-теги через TextDisplay'и
- Обработка чата
    - Глобальный & локальный чат (+ API для добавления других каналов чата)
    - Личные сообщения
    - Игнорирование игроков
    - Упоминания, обработка URL, форматирование с стилями и замены (плейсхолдеры для игроков для использования в чате, например deaths, playtime, ping)
- Обработка таба (списка игроков)
    - Header, footer
    - Имя игрока в табе
    - Objective's
- Объявления об ивентах и трансляциях

## Использование
Вы можете получить jar во вкладке [Actions](https://github.com/modoruru/hitori-ux/actions). Модуль собран после почти каждого коммита.\
Также, вы можете получить модуль из [Releases](https://github.com/modoruru/hitori-ux/releases) (если они есть).

После скачивания jar, просто поместите его в папку hitori. После, перезагрузите сервер.

## API
Возможно, вам потребуется использовать хранилище или другие API этого модуля.\
Поэтому, модуль опубликован через [JitPack](https://jitpack.io/)

Последняя версия: [![](https://jitpack.io/v/modoruru/hitori-ux.svg)](https://jitpack.io/#modoruru/hitori-ux)

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
