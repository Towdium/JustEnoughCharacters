[![][2]][1] 
[![][3]][1]
[![][8]][9]

# 通用拼音搜索

## 有啥用

使用这个模组，你可以在市面上绝大部分模组中使用拼音搜索。这包括了各类模组的手册，物流模组的容器，还有各种稀奇古怪的场景。简体和繁体都可以使用。本模组默认使用全拼拼法，对于繁体中文用户，你可以切换到注音拼法。你可以使用原文，全拼，声母的各种组合进行搜索，你可以使用声调或者忽略声调，任何你能想到的组合都可以使用。

## 实现原理

这是一个 coremod，主要由一下几部分组成：

- __可配置逻辑__: 这一部分转换器基于[在线转换列表][4]，对字节码中的特定模式进行替换。这包括 `String.contains`, `Regex.mather`, `StringsKt.contains`， 以及原版 MC 提供的缓存结构 `net.minecraft.client.util.SuffixArray`。

- __硬编码逻辑__: 这一部分转换器用于处理可配置逻辑以外的实现方法，目前包括 Psi 和 JEI 的兼容。对于 JEI，他将 JEI 的缓存结构 `mezz.jei.suffixtree.GeneralizedSuffixTree` 替换为我实现的结构。对于 Psi，它将原有的复杂的排序系统转换为了基于 `String.contains` 的过滤系统。

对于拼音匹配，它提供两组逻辑：

- __实时匹配__： 对于实时匹配，本模组提供了基于 NFA 的实现。时间复杂度为 O(sw)， s 和 w 分别为文本串和拼音串的长度。对于 s=10，w=10，每秒钟大致可以执行 100 万次匹配。

- __缓存匹配__： 这里缓存使用广义后缀树。时间复杂度为 O(w+n)，这里 w 是拼音串的长度，n 是搜索结果数。对于 w=10，n=100k 搜索大致需要 10 毫秒。结构的空间复杂度为 O(s)，这里 s 为总字符数。 

对于形似 `String.contains`，`Regex.mather` 和 `StringsKt.contains` 的实时匹配逻辑，他们会被替换为上述的实时匹配逻辑。至于其他基于缓存的逻辑，他们会被替换为上述的缓存逻辑。

## 如何使用

对于普通用户，直接安装就完事了。配置文件里有一些常用的模糊音选项可以修改。对于高端玩家，这边还有一些其他工具，说不定有用。

- __/jech profile__：这个命令会扫描所有模组文件，并列出所有可以被可配置逻辑替换的函数位置。

- __配置文件__: 配置文件里可以控制本模组几乎所有的行为。比较重要的是你可以给可配置逻辑添加额外的替换项。如果你本身就是程序员的话，基于上一个命令给出的扫描结果，应该可以轻松兼容大部分需要的模组。如果你有相关信息的话，也欢迎告诉我。

## 致开发者

本模组目前不提供 API，不过你要是真的需要也可以有。我估计绝大部分人的实现都是在可配置逻辑范畴以内的。在这种情况下，你只需要告诉我你对应的实现位置，其它问题我都可以解决。至于兼容性，我建议你把相关的实现写在有名的语境内，也就是说不要在 lambda 或者匿名类之类的地方，否则函数名可能会日常跑偏。对于这类兼容，[Correlated 的实现][5] 是一个很好的例子。至于我的 `contains` 的实现，他在纯英文情景下应该和 JRE 内置的方法一致，除非出现 bug。至于性能，它确实要慢一些，但是它在 JEI 环境下都能流畅跑，所以应该不会有什么问题。

## 致谢

3.0.0 之前的版本包含了 pinyin4j 用于拼音数据的查找，非常感谢。在之后的版本里，本模组使用专门开发的拼音框架，其中拼音数据来自于 [地球拼音][6] 和 [pinyin-data][7].

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