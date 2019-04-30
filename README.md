[![][2]][1] 
[![][3]][1]
[![][8]][9]
[中文文档][10]

# Just Enough Characters Mod

## What it does

This is a little mod to make other mods search by pinyin in a Chinese environment. It works for both simplified and traditional Chinese, you can use Quanpin or Phonetic spelling to search. With this mod, you can search by raw text, full pinyin, consonant, with or without tone, or anything you can imagine.

## How it works

### Code injection

This mod is a coremod using a plugin based system for specific targets. It mainly contains following parts:

- __Configurable__: These plugins targets at specific pieces in bytecode according to [online feed][4] fetched at runtime. It includes support for `String.contains`, `Regex.mather`, `StringsKt.contains` and cache structure `net.minecraft.client.util.SuffixArray` provided by vanilla Minecraft.

- __Fixed__: It now includes support for Psi and JEI since their implementation does not match any configurable targets. For JEI, it transfers JEI cache (currently 
`mezz.jei.suffixtree.GeneralizedSuffixTree`) to my cache. For Psi, it transforms Psi's sophisticated ranking system to a simple one based on `String.contains`.

### Pinyin match

It has two sets of logic for Pinyin matching:

- __Uncached__: This method is implemented based on NFA for real-time matching. The time complexity is O(sw), with s and w for length of text string and pinyin string. For s=10 and w=10, it does around 1 million matches per second searching in pinyin.

- __Cached__: The cache structure is generalized suffix tree with extra support for pinyin search. The time complexity is O(w+n), with w for length of pinyin string and n for amount of results. For w=10, n=100k, it takes around 10 seconds. Space complexity is O(s), with s for amount of characters in total. 

 One is , based on NFA. Another is 

## How to use

For average users, installing it will do all the work. For advanced
users, there are some tools to play with:

### /jech profile

It collects all methods with `String.contains` call and `Regex.matcher` 
call then put them in a report, of which the format is optimized for
machines to read.

### Config

You can manually add entry to support `String.contains` and `Regex.matcher`
call. If you are an experienced programmer, you can easily support other
mods that is not currently supported. You are welcomed to notify me of
these information.

## For developers

This mod provides no API currently, but there will be if there is need
for this. I think most of people will do things in a `contains` way.
So make your mod supported, all you need to do is letting me know the
location of your `contains` call, then the method call will be transformed
to my implementation that supports Chinese. Everything is done.

To keep better compatibility, I would suggest not to let me transform
`contains` called in lambda functions because this mod distinguish methods
by function names and, as you know, the lambda functions have no name.
More specifically, they have automatically generated names which could
change at any time you modify the class. So a suggested way is wrap the
calls into a static method, or whatever with a stable name. A good 
example can be found in [code of Correlated][5], the method `contains`.

If you want to know my implementation of `contains`, here is some words.
It can handle any mixture of English and Chinese, nearly all of the 
circumstances you could meet. For English-only strings, it will work 99%
the same as the original implementation in JRE. If it's not the same,
it should be considered a bug. 

For performance, it has several times the cost of the original one
for English-only strings, and even more time for strings containing
Chinese characters. But after testing, it does not generate significant
lag when used in JEI, so I it should be fine for most of the developers.

## Credits

In versions before 3.0.0, this repository contained a simplified version 
of pinyin4j as pinyin database. Thanks a lot for their work. In versions
after 3.0.0, a new pinyin library is developed and used based on a
mixture of [terra pinyin][6] and [pinyin-data][7].

Have fun!

[1]: https://minecraft.curseforge.com/projects/just-enough-characters
[2]: http://cf.way2muchnoise.eu/full_just-enough-characters_downloads.svg
[3]: http://cf.way2muchnoise.eu/versions/just-enough-characters.svg
[4]: https://github.com/Towdium/JustEnoughCharacters/blob/1.12.0/feed.json
[5]: https://github.com/elytra/Correlated/blob/1.12.1/src/main/java/com/elytradev/correlated/C28n.java
[6]: https://github.com/rime/rime-terra-pinyin
[7]: https://github.com/mozillazg/pinyin-data
[8]: https://img.shields.io/discord/517485644163973120.svg?logo=discord
[9]: https://discord.gg/M3fNfTW
[10]: https://github.com/Towdium/JustEnoughCharacters/blob/1.12.0/README_CN.md
