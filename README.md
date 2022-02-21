## 一款能将Java源码转成[dexmaker](https://github.com/linkedin/dexmaker)代码的IDEA插件，方便开发和测试

<br/>

### 插件起源：
去年年底在wanandroid每日一问：[如何构造一个 hide interface 的实现类？](https://www.wanandroid.com/wenda/show/20867)问题中，***残页***同学的回答说，可以通过stub + compileOnly的方式用动态代理实现访问权限是package的接口。

我当时就想：用stub+动态代理还是有点麻烦，不是有个叫dexmaker的库可以在运行时动态生成dex吗？不知道能不能直接实现一个hidden的interface？

但我此前又没用过dexmaker，不知道代码要怎么写，觉得挺复杂的。 于是网上搜了一下生成dexmaker代码的工具，发现不但没有，就连dexmaker的相关文章也寥寥无几。

看了官方文档生成Fibonacci class的例子之后，忽然想到：这些代码都是有固定的规则和逻辑，那应该能做成一个自动生成的插件啊（实在不想自己动手写dexmaker代码）！之前不是有个IDEA插件可以生成ASM代码嘛？

于是反编译了那个*ASM Bytecode Viewer*插件，发现它的核心部分是直接使用ASM提供的jar包来实现的！原来ASM就有提供生成ASM代码的api！dexmaker为什么你就不提供一下这个东西啊！哈哈哈

接着又看了一下这个ASM的jar包，发现核心生成类***ASMifier***（这也是本项目的名字来源，它叫***ASMifier***，我就叫***D***(ex)***M***(aker)***ifier***吧，哈哈）继承了Printer，Printer结合ClassReader可以把class文件的指令都读取出来……

<br/>

### 实现原理：
参照了ASMifier的做法，也弄了一个继承自Printer的类，并在重写的`visitMethod`、`visitInsn`等方法里按照dexmaker的规则去输出dexmaker代码……

<br/>

### 使用方式：
插件安装后，在编辑器中右键菜单下方可看到 “View DexMaker Code” 选项：

![preview](https://github.com/wuyr/DMifier/raw/main/previews/1.png)

或在主菜单 *View* 底部点击：

![preview](https://github.com/wuyr/DMifier/raw/main/previews/2.png)

成功转换后会以Dialog的形式把结果弹出，直接复制到自己的代码里即可：

![preview](https://github.com/wuyr/DMifier/raw/main/previews/3.png)

<br/>

### 安装：
**在线安装：**

插件还在不断完善中，暂时未上传到JetBrain marketplace上。

**本地安装：**

到 [releases](https://github.com/wuyr/DMifier/releases) 里下载最新的 *DMifierPlugin.zip* 后拖把它拖进IDE中并重启。

<br/>

### 开发进度：
**未实现的指令：**

*NOP*、

*POP2*、

*DUP_X1*、

*DUP_X2*、

*DUP2*、

*DUP2_X1*、

*DUP2_X2*、

*SWAP*、

*JSR*、

*MULTIANEWARRAY*（创建多维数组）、

*MONITORENTER*、*MONITOREXIT*（synchronized语句）、

（try catch语句，dexmaker没有提供moveException方法）

<br/>

**不支持的指令：**

*INVOKEDYNAMIC*（Dalvik 字节码没有对应的指令）、

*TABLESWITCH*、*LOOKUPSWITCH*（dexmaker没有提供相关api）

<br/>

**坑：**

对了，这里还有个dexmaker的坑，就是if语句 `if(a >= b)`，当a=0时，在生成dex之后，a和b的顺序会反过来，也就是变成了 `if(b >= 0)`。 提了issue一周没人回答，才发现dexmaker在去年2月份就停止了更新！坑啊！！！

<br/>

**后续计划：**
 - 优化转换结果显示方式；

 - 加入直接查看生成的dex文件的smali以及转成class后的代码；

<br/>

### 更新日志：

- **0.1** 提供基本转换功能。
