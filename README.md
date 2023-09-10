[![][2]][1] 
[![][3]][1]
[![][4]][5]

# JustEnoughCharacters

## 作用

使用这个模组，你可以在市面上绝大部分模组中使用拼音搜索。这包括了各类模组的手册，物流模组的容器，还有各种稀奇古怪的场景。简体和繁体都可以使用。你可以使用原文，全拼，声母的各种组合进行搜索，你可以使用声调或者忽略声调，任何你能想到的组合都可以使用。模组默认使用全拼拼法，也可以通过修改配置切换到注音或者双拼。当然，双拼场景下字形辅助码是不能用的，但是你可以像其他拼法一样使用声调来过滤。任何不支持的模组搜索都欢迎到 issue 区提给我。

尽管有一些个人实现的代码库已经在当前版本支持 config GUI 了，Forge 在当前版本仍然没有官方支持，因此本模组目前也不开放图形化配置。你可以修改配置文件或者使用 `/jech` 命令来配置。

> 一个意外的好处是，和 JEI 一同使用时，JEI 的内存占用可能会缩减 100M 左右。

## 原理

由于核心匹配逻辑已经分离到 [PinIn][9] 这个项目了，本模组当前版本的工作原理极为简单。我们只需要将各模组文本匹配相关的代码找到，然后替换成兼容拼音的实现即可。我们将相关的调用位置填写在 [generate.py][10] 里，然后基于 Forge 现版本的 coremod 机制，使用脚本直接生成所需的 coremod，编译时打入模组包中即可。当然，有一些模组需要特别的兼容处理，这一部分内容你可以在 [这里][11] 找到。

本项目和 PinIn 的核心匹配逻辑，在肉眼可见的将来我还是会保持维护的，这方面不必担心。

## 开发

尽管直到目前该项目的贡献者屈指可数，给该项目贡献代码仍然是十分简单的。如果你发现有某个模组不支持拼音搜索，你只需要执行 `/jech profile` 命令获得一份全量搜索报告，排查该模组相关的调用栈（需要亿点点技巧），然后提交上来即可。当然，如果能力有限，直接把模组名甩给我也是欢迎的。

关于当前跨版本跨平台的开发，当前计划是统一使用一套全局target表，不区分平台，因为搜索代码很大程度上不是平台相关的。
同时，每个版本维护一套版本相关的target表，防止意外情况发生（当然，它们都是在编译的时候就已经决定了的）。

## 致谢

- 本模组更新到 1.16 的绝大部分工作是由 [yzl210][8] 完成的。
- 本模组更新到 1.18 的绝大部分工作是由 [yzl210][8] 和 [vfyjxf][13] 完成的。
- 本模组对于 1.16 的一吨 mod 的支持是由 [Death-123][12] 完成的。
- 本模组的核心库 PinIn 中使用的拼音数据来自 [地球拼音][6] 和 [pinyin-data][7]。
- 本模组的多版本开发模板基于[3TUSK][14]的[Paramita][15]。
- 在Fabric平台实现使用ASM类库修改类的操作方法来自[SpASM][16]

[1]: https://minecraft.curseforge.com/projects/just-enough-characters
[2]: http://cf.way2muchnoise.eu/full_250702_downloads.svg
[3]: http://cf.way2muchnoise.eu/versions/250702.svg
[4]: https://img.shields.io/discord/517485644163973120.svg?logo=discord
[5]: https://discord.gg/M3fNfTW
[6]: https://github.com/rime/rime-terra-pinyin
[7]: https://github.com/mozillazg/pinyin-data
[8]: https://github.com/yzl210
[9]: https://github.com/Towdium/PinIn
[10]: https://github.com/Towdium/JustEnoughCharacters/blob/1.16/generate.py
[11]: https://github.com/Towdium/JustEnoughCharacters/tree/1.16/src/main/resources/me/towdium/jecharacters/scripts
[12]: https://github.com/Death-123
[13]: https://github.com/vfyjxf
[14]:https://github.com/3TUSK
[15]:https://github.com/3TUSK/Paramita
[16]:https://github.com/mineLdiver/SpASM