Developer API
Intro
LuckPerms has a complete developer API, which allows other plugins on the server to read and modify LuckPerms data, and easily integrate LuckPerms deeply into existing plugins and systems.

Versioning
The API uses Semantic Versioning, meaning whenever a non-backwards compatible change is made, the major version will increment. You can rest assured knowing your integration will not break between versions, providing the major version remains the same.

The current API release is 5.5.

The API package in LuckPerms is net.luckperms.api.
JavaDocs are available either in a standard JavaDoc layout, or within the API source code.
Changelogs
Version 2.x represented the initial release of the API.
Version 3.x (19th Feb 17) introduced a number of backwards incompatible changes. [changelog]
Version 4.x (7th Nov 17) introduced a number of backwards incompatible changes. [changelog]
Version 5.x was a complete rewrite of the API. Bridging tools are provided to maintain compatibility with older versions.
Quick start guide
Adding LuckPerms to your project
Maven
Gradle
Manual
Obtaining an instance of the API
Using the Bukkit ServicesManager
Using the Sponge ServicesManager
Using the singleton
Useful information
Thread safety
Immutability
Blocking operations
Using CompletableFutures
Asynchronous events & callbacks
Adding LuckPerms to your project
The API artifact is published to the Maven Central repository.

Maven
If you're using Maven, simply add this to the dependencies section of your POM.

<dependencies>
    <dependency>
        <groupId>net.luckperms</groupId>
        <artifactId>api</artifactId>
        <version>5.5</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
Gradle
If you're using Gradle, you need to add these lines to your build script.

Groovy DSL:
repositories {
    mavenCentral()
}

dependencies {
    compileOnly 'net.luckperms:api:5.5'
}
Kotlin DSL:
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.luckperms:api:5.5")
}
Manual
If you want to manually add the API dependency to your classpath, you can obtain the jar by:

Navigating to https://repo1.maven.org/maven2/net/luckperms/api/
Selecting the version you wish to use
Downloading the jar titled api-x.x.jar
Obtaining an instance of the API
The root API interface is LuckPerms. You need to obtain an instance of this interface in order to do anything.

It can be obtained in a number of ways.

Using the Bukkit ServicesManager
When the plugin is enabled, an instance of LuckPerms will be provided in the Bukkit ServicesManager. (obviously you need to be writing your plugin for Bukkit!)

RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
if (provider != null) {
    LuckPerms api = provider.getProvider();
    
}
Using the Sponge ServicesManager
When the plugin is enabled, an instance of LuckPerms will be provided in the Sponge ServicesManager. (obviously you need to be writing your plugin for Sponge!)

Optional<ProviderRegistration<LuckPerms>> provider = Sponge.getServiceManager().getRegistration(LuckPerms.class);
if (provider.isPresent()) {
    LuckPerms api = provider.get().getProvider();
    
}
Using the singleton (static access)
When the plugin is enabled, an instance of LuckPerms can be obtained statically from the LuckPermsProvider class. (this will work on all platforms)

Note: this method will throw an IllegalStateException if the API is not loaded.

LuckPerms api = LuckPermsProvider.get();
Useful information
Now you've added the API classes to your project, and obtained an instance of the LuckPerms, you're almost ready to start using the API. However, before you go any further, please make sure you read and understand the information below.

Thread safety
All LuckPerms internals are thread-safe. You can safely interact with the API from async scheduler tasks (or just generally from other threads)
This also extends to the permission querying methods in Bukkit/Bungee/Sponge. These can be safely called async when LuckPerms is being used as the permissions plugin.
Immutability
In cases where methods return classes from the Java collections framework, assume that the returned methods are always immutable, unless indicated otherwise. (in the JavaDocs)
This means that you cannot make changes to any returned collections, and that the collections are only an accurate representation of the underlying data at the time of the method call.
Blocking operations
Some methods are not "main thread friendly", meaning that if they are called from the main Minecraft Server thread, the server will lag.
This is because many methods conduct I/O with either the file system or the network.
In most cases, these methods return CompletableFutures.
Futures can be an initially complex paradigm for some users - however, it is crucial that you have at least a basic understanding of how they work before attempting to use them.
As a general rule, it is advised that if it's convenient to do so, you conduct as much work with the API as possible within async scheduler tasks. Some methods don't return futures, but may still involve a number of relatively complex computations.
Using CompletableFutures
This is a super quick guide. If you'd like more comprehensive info, see the CompletableFuture or CompletionStage JavaDoc pages.

For the purposes of explaining, take the following method in the ActionLogger class.

CompletableFuture<ActionLog> getLog();
After calling the method, we get a CompletableFuture<ActionLog> - the object we actually want is the ActionLog. The CompletableFuture represents the result of some computation (in this case the computation to obtain the ActionLog), and provides us with methods to obtain the ActionLog object.

If the context of our method call is already asynchronous (if we're calling the method from an async scheduler task), then we can do-away with the future entirely.

/*
  Calling this method "requests" an ActionLog from the API.
  
  However, it's unlikely that the log will be available immediately...
  We need to wait for it to be supplied.
*/
CompletableFuture<ActionLog> logFuture = actionLogger.getLog();

/*
  Since we're already on an async thread, it doesn't matter how long we
  have to wait for the elusive Log to show up.
  
  The #join method will block - and wait until the Log has been supplied,
  and then return it.
  
  If for whatever reason the process to obtain a ActionLog threw an exception,
  this method will rethrow an the same exception wrapped in a CompletionException
*/
ActionLog log = logFuture.join();
An alternative to using #join is to register a callback with the CompletableFuture, to be executed once the Log is supplied.

If we need to use the instance on the main server thread, then a special executor can be passed to the callback is executed on the server thread.

// Create an executor that will run our callback on the server thread.
Executor executor = runnable -> Bukkit.getScheduler().runTask(plugin, runnable);

// Register a callback with the future.
logFuture.whenCompleteAsync(new BiConsumer<ActionLog, Throwable>() { // can be reduced to a lambda, I've left it as an anonymous class for clarity
    @Override
    public void accept(ActionLog log, Throwable exception) {
        if (exception != null) {
            // There was some error whilst getting the log.
            return;
        }

        // Use the log for something...
    }
}, executor);
If you don't care about errors, this can be simplified further.

logFuture.thenAcceptAsync(log -> { /* Use the log for something */ }, executor);
The CompletableFuture class can initially be very confusing to use (it's still a relatively new API in Java!), however it is a great way to encapsulate async computations, and in the case of Minecraft, ensures that users don't accidentally block the server thread waiting on lengthy I/O calls.

Asynchronous events & callbacks
The vast majority of LuckPerms' work is done in async tasks away from the server thread.
With that in mind, it would be silly to call LuckPerms events synchronously - meaning that, without exception, all events listeners are called asynchronously.
Please keep in mind that many parts of Bukkit, Sponge and the Minecraft server in general are not thread-safe, and should only be interacted with from the server thread. If you need to use Bukkit or Sponge methods from within LuckPerms event listeners or callbacks, you need to perform your action using the scheduler.



Developer API Usage
This page shows some sample usages of the LuckPerms API, which is introduced here.

As well as this documentation, we also have the api-cookbook. This is an example Bukkit plugin which uses the API to perform certain common functions.

Index
Player group membership check
Player group membership lookup
Getting a LuckPerms User object
Distinction between online & offline players
Loading data for players
Getting a LuckPerms Group or Track object
Saving changes
The Node object
Creating new nodes
Modifying existing nodes
Reading user/group data
Modifying user/group data
Context
Important classes
Registering ContextCalculators
Querying active contexts/query options
CachedData
Performing permission checks
Getting prefixes and suffixes
Getting metadata
Store and query custom metadata
Events
Event listeners
Listening for changes to user cached data
Listening for changes to permissions/parent groups/etc
Checking if a player is in a group
Checking for group membership can be most easily achieved using hasPermission checks.

public static boolean isPlayerInGroup(Player player, String group) {
    return player.hasPermission("group." + group);
}
However, keep in mind that anyone with server operator status or * permissions will also have these permissions.

Finding a players group
We can use the method above with a list of "possible" groups in order to find a player's group.

public static String getPlayerGroup(Player player, Collection<String> possibleGroups) {
    for (String group : possibleGroups) {
        if (player.hasPermission("group." + group)) {
            return group;
        }
    }
    return null;
}
Remember to order your possibleGroups list by priority. e.g. owner first, member last.

Obtaining a User instance
A User in LuckPerms is simply an object which represents a player on the server, and their associated permission data.

Distinction between online & offline players
In order to conserve memory usage, LuckPerms will only load User data when it absolutely needs to.

Meaning:

Online players are guaranteed to have an associated User object loaded already.
Offline players may have an associated User object loaded, but they most likely will not.
This makes getting a User instance a little complicated, depending on if the Player is online or not.

Loading data for players
If the player is already online
If we know the player is connected, LuckPerms will already have data in memory for them.

It's as simple as...

Player player = ...;
User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
Or if you only have a UUID...

User user = luckPerms.getUserManager().getUser(uuid);
However, remember that this instance may not represent the user's most up-to-date state. If you want to make changes, it's a good idea to request for the user's data to be loaded again (read on...).

If the player isn't (or might not be) online
Let's assume we want to load some data about a user - but we only have their unique id.

The first thing we need to do is obtain the UserManager. This object is responsible for handling all operations relating to Users. The user manager provides a method which lets us load a User instance, appropriately named loadUser.

The method returns a CompletableFuture (explained here).

We can simply attach a callback onto the future to apply the action.

UserManager userManager = luckPerms.getUserManager();
CompletableFuture<User> userFuture = userManager.loadUser(uniqueId);

userFuture.thenAcceptAsync(user -> {
    // Now we have a user which we can query.
    // ...
});
If the player isn't (or might not be) online & we want to return something
The callback approach works well if you don't need to actually "return" anything. It performs all of the nasty i/o away from the main server thread, and handles everything in the background.

But what if we need data now? Well, that's where it gets fun. Unfortunately, there's no straightforward answer - but you effectively have two options.

Define a blocking method, which will be (kind of) simple, but will lag the server if it's not called async
Embrace CompletableFutures and callbacks
The first option effectively comes down to this...

public User giveMeADamnUser(UUID uniqueId) {
    UserManager userManager = luckPerms.getUserManager();
    CompletableFuture<User> userFuture = userManager.loadUser(uniqueId);

    return userFuture.join(); // ouch! (block until the User is loaded)
}
You can then do whatever you want with the user instance - but remember, this should only ever be called from an async task!

The other option is to embrace callbacks.

In an ideal world, we'd be able to do something like this, without any consequences.

public boolean isAdmin(UUID who) {
    User user = luckPerms.getUserManager().loadUser(who);

    Collection<Group> inheritedGroups = user.getInheritedGroups(user.getQueryOptions());
    return inheritedGroups.stream().anyMatch(g -> g.getName().equals("admin"));
}

public void informIfAdmin(CommandSender sender, UUID who) {
    if (isAdmin(who)) {
        sender.sendMessage("Yes! That player is an admin!");
    } else {
        sender.sendMessage("No, that player isn't an admin.");
    }
}
However, we can't, because #loadUser returns a CompletableFuture - as it performs lots of expensive database queries to produce a result.

The solution? More futures!

public CompletableFuture<Boolean> isAdmin(UUID who) {
    return luckPerms.getUserManager().loadUser(who)
        .thenApplyAsync(user -> {
            Collection<Group> inheritedGroups = user.getInheritedGroups(user.getQueryOptions());
            return inheritedGroups.stream().anyMatch(g -> g.getName().equals("admin"));
        });
}

public void informIfAdmin(CommandSender sender, UUID who) {
    isAdmin(who).thenAcceptAsync(result -> {
        if (result) {
            sender.sendMessage("Yes! That player is an admin!");
        } else {
            sender.sendMessage("No, that player isn't an admin.");
        }
    });
}
To summarise, there are two ways to obtain a user.

Using UserManager#getUser or PlayerAdapter#getUser
Always returns a result for online players
Is "main thread friendly" (can be called sync)
Will sometimes (but usually not) return a result of offline players
Using UserManager#loadUser
Returns a future
Can either be used alongside callbacks, or as part of a blocking method which is only ever called async
Always works for both offline/online users
Obtaining a Group/Track instance
Grabbing a Group or Track is much more simple, as they are always kept loaded in memory.

Simply...

Group group = luckPerms.getGroupManager().getGroup(groupName);
if (group == null) {
    // group doesn't exist.
    return;
}

// now we have a group, and can apply whatever action we want.
group.doSomething(...);
You can do exactly the same for Tracks using the TrackManager#getTrack method.

If you need to get up-to-date data (a good idea if you're making changes), then just call loadGroup or loadTrack instead, respectively.

Saving changes
After making changes to a user/group/track, you have to save the changes back to the storage provider. It's pretty easy.

public void addPermission(User user, String permission) {
    // Add the permission
    user.data().add(Node.builder(permission).build());

    // Now we need to save changes.
    luckPerms.getUserManager().saveUser(user);
}
There is also a handy modify* method which handles loading and saving for you.

public void addPermission(UUID userUuid, String permission) {
    // Load, modify, then save
    luckPerms.getUserManager().modifyUser(userUuid, user -> {
        // Add the permission
        user.data().add(Node.builder(permission).build());
    });
}
The same methods also exist for groups and tracks.

The basics of Node
The Node interface is the core data class in LuckPerms.

Most simply, it represents a "permission node". However, it actually encapsulates far more than just permission assignments. Nodes are used to store data about inherited groups, as well as assigned prefixes, suffixes and meta values.

Combining these various states into one object (a "node") means that a holder only has to have one type of data set (a set of nodes) in order to take on various properties.

The Node interface provides a number of methods to read the attributes of the node, as well as methods to query and extract additional state and properties from these settings.

Nodes have the following attributes:

key - the key of the node
value - the value of the node (false for negated)
context - the contexts required for this node to apply
expiry - the time when this node should expire
There are a number of node types, all of which are extensions of the base Node class.

PermissionNode - represents an assigned permission
RegexPermissionNode - represents an assigned regex permission
InheritanceNode - marks that the holder should inherit data from another group
PrefixNode - represents an assigned prefix
SuffixNode - represents an assigned suffix
MetaNode - represents an assigned meta option
WeightNode - marks the weight of the object holding the node
DisplayNameNode - marks the display name of the object holding the node
Creating new node instances
To obtain a Node, you use NodeBuilders.

If you just have a "key" and are unsure which category of node it falls into, you can simply use Node.builder().

// build any type of node
Node node = Node.builder("some.node.key").build();

// and with extra properties!
Node node = Node.builder("some.node.key")
        .value(false)
        .expiry(Duration.ofHours(1))
        .withContext(DefaultContextKeys.SERVER_KEY, "survival")
        .build();

// note: all of the following classes extend from Node

// build a permission node
PermissionNode node = PermissionNode.builder("my.permission").build();

// build a regex permission node
RegexPermissionNode node = RegexPermissionNode.builder(pattern).build();

// build an inheritance node
InheritanceNode node = InheritanceNode.builder(group).build();

// build a prefix node
PrefixNode node = PrefixNode.builder("[Some Prefix]", 100).build();

// build a suffix node
SuffixNode node = SuffixNode.builder("[Some Suffix]", 150).build();

// build a metadata node
MetaNode node = MetaNode.builder("some-key", "some-value").build();

// build a weight node
WeightNode node = WeightNode.builder(25).build();

// build a display name node
DisplayNameNode node = DisplayNameNode.builder("SeniorModerator").build();
Modifying existing nodes
Nodes are immutable - meaning their attributes cannot be changed. However, we can easily create a new node based upon the properties of an existing one.

e.g.

Node negated = node.toBuilder().value(false).build();
Reading user/group data
Users and Groups both inherit from a super interface called PermissionHolder. This interface defines most of the shared permission functionality in users and groups.

As explained above, most data held by users/groups are contained within Node instances. This means that there are only a few methods to think about. However, they all do slightly different things!

Importantly, all of the methods below return immutable collections. You cannot make changes to the returned connections.

.getNodes()
The method signature is:

Collection<Node> getNodes()
This method returns an un-flattened (or squashed) collection of the user/groups nodes.
Entries nearer the start of the collection (index zero) have priority over nodes at the end.
This view does not consider inherited data.
It's a relatively cheap call, and will return quite quickly.

You can use the Stream API to easily filter the returned data to find what you need. For example, if you wanted to get a list of groups a holder inherits from, you could use something like this:

Set<String> groups = user.getNodes().stream()
    .filter(NodeType.INHERITANCE::matches)
    .map(NodeType.INHERITANCE::cast)
    .map(InheritanceNode::getGroupName)
    .collect(Collectors.toSet());
You can make this a bit simpler by passing the node type as a parameter!

Set<String> groups = user.getNodes(NodeType.INHERITANCE).stream()
    .map(InheritanceNode::getGroupName)
    .collect(Collectors.toSet());
Or even more complicated queries, like finding the max priority of a temporary prefix held on a specific server.

int maxWeight = user.getNodes(NodeType.PREFIX).stream()
    .filter(Node::hasExpiry)
    .filter(n -> n.getContexts().getAnyValue(DefaultContextKeys.SERVER_KEY)
        .map(v -> v.equals("factions")).orElse(false))
    .mapToInt(ChatMetaNode::getPriority)
    .max()
    .orElse(0);
If you need to do a more specific lookup or check, prefer using one of the other methods (described later) to avoid iterating over the whole collection of nodes.

.getDistinctNodes()
The method signature is:

SortedSet<Node> getDistinctNodes();
This method returns a sorted view of #getNodes. If you are not worried about ordering, it's faster to use #getNodes.
The nodes are sorted according to "priority order". As the returned type is a set, duplicate elements may be missing.
This view does not consider inherited data.
.resolveInheritedNodes()
The method signature is:

Collection<Node> resolveInheritedNodes(QueryOptions queryOptions)
This method returns an un-flattened (or squashed) list of the user/groups nodes, and all nodes they inherit from their parents.
Entries nearer the start of the list (index zero) have priority over nodes at the end.
This view does consider inherited data. If you don't need this, use the getNodes method instead.
The QueryOptions parameter encapsulates the lookup settings for the query. This class is explained in a later section. If you're not worried particularly about filtering by context, simply use QueryOptions.nonContextual().

Modifying user/group data
User/group data can be modified by adding and removing Nodes from the holders data. This can be done by calling #data and calling the methods on the returned NodeMap.

Here is an example of adding a permission to a user:

DataMutateResult result = user.data().add(Node.builder("your.node.here").build());
Don't forget to save your changes!

The basics of Context
Contexts are an important concept in LuckPerms, and are introduced here. They are encapsulated within the API by a few important classes.

A very basic overview is that:

Context in the most basic sense simply means the circumstances where something will apply.

A single "context" consists of a key and a value, both strings. The key represents the type of context, and the value represents the setting of the context key.

Contexts can be combined with each other to form so called "context sets" - simply a collection of context pairs.

Context keys are case-insensitive, and will be converted to lowercase by all implementations. Values however are case-sensitive. Context keys and values may not be null or empty. A key/value will be deemed empty if it's length is zero, or if it consists of only space characters.

Important classes
ContextSet
A "context set" is simply a set of contexts.

Internally, a context set is effectively a Multimap<String, String>, or a <Map<String, Collection<String>>, but importantly, it is not a Map<String, String>.

Keys can be mapped to multiple values.

The ContextSet interface defines a number of methods which can be used to interact with context set implementations. These methods should be fairly self explanatory - and are sufficiently explained in the Javadocs.

ImmutableContextSet
An immutable implementation of ContextSet. You can obtain an instance in a number of ways.

ImmutableContextSet set1 = ImmutableContextSet.empty();  

ImmutableContextSet set2 = ImmutableContextSet.of("world", "world_nether");

ImmutableContextSet set3 = ImmutableContextSet.builder()  
    .add("world", "world_nether")
    .add("server", "survival")
    .build();

Map<String, String> map = new HashMap<>();
map.put("region", "something");

ImmutableContextSet.Builder builder = ImmutableContextSet.builder();
map.forEach(builder::add);

ImmutableContextSet set4 = builder.build();
You can of course also create an ImmutableContextSet by first creating (or obtaining) a MutableContextSet and converting it.

MutableContextSet set = MutableContextSet.create();
set.add("something", "something");

ImmutableContextSet immutableSet = set.immutableCopy();
MutableContextSet
A mutable implementation of ContextSet. You can obtain an instance in a number of ways.

MutableContextSet set1 = MutableContextSet.create();
set1.add("world", "text");

MutableContextSet set2 = MutableContextSet.of("world", "world_nether");

Map<String, String> map = new HashMap<>();
map.put("region", "something");

MutableContextSet set3 = MutableContextSet.create();
map.forEach(set3::add);

set3.removeAll("region");
To edit an ImmutableContextSet, you can make a "mutable copy" of it.

ImmutableContextSet set = ImmutableContextSet.of("something", "something");

MutableContextSet mutableCopy = set.mutableCopy();
mutableCopy.add("something", "something-else");
Registering ContextCalculators
A "subject" (a player in most cases) is just an object which can have contexts applied to them.

In other words, a "subject" is an object which has an active context set. A ContextCalculator is an object which determines the "active" contexts for a given type of Subject.

The subject type varies between platforms.

Platform	Subject type
Bukkit	org.bukkit.entity.Player
BungeeCord	net.md_5.bungee.api.connection.ProxiedPlayer
Sponge	org.spongepowered.api.service.permission.Subject
Fabric	net.minecraft.server.network.ServerPlayerEntity
Forge	net.minecraft.server.level.ServerPlayer
Nukkit	cn.nukkit.Player
Velocity	com.velocitypowered.api.proxy.Player
In order to provide your own context, you need to create and register a ContextCalculator.

For example, if I wanted to provide a context for the player's gamemode, in order to set permissions for players only when they are in creative, I'd create a calculator as follows. The estimatePotentialContexts method can be added, but is not necessary, to show context suggestions in the tab completion.

public class CustomCalculator implements ContextCalculator<Player> {

    @Override  
    public void calculate(Player target, ContextConsumer contextConsumer) {
        contextConsumer.accept("gamemode", target.getGameMode().name());
    }
    
    @Override
    public ContextSet estimatePotentialContexts() {
        ImmutableContextSet.Builder builder = ImmutableContextSet.builder();
        for (GameMode gameMode : GameMode.values()) {
            builder.add("gamemode", gameMode.name().toLowerCase());
        }
        return builder.build();
    }
    
}
Then register it using

luckPerms.getContextManager().registerCalculator(new CustomCalculator());
Querying active contexts/query options
You can query the "active" contexts/query options of a Subject using the ContextManager.

If you already have an instance of the subject type, you can query directly using this.

Player player = ...;

ImmutableContextSet contextSet = luckPerms.getContextManager().getContext(player);
QueryOptions queryOptions = luckPerms.getContextManager().getQueryOptions(player);
If you only have a User, you can still perform a lookup, however, a result will only be returned if the corresponding subject (player) is online.

Optional<ImmutableContextSet> contextSet = luckPerms.getContextManager().getContext(user);
Optional<QueryOptions> queryOptions = luckPerms.getContextManager().getQueryOptions(user);
If you absolutely need to obtain an instance, you can fallback to the server's "static" context/query option. (these are formed using calculators which provide contexts/query options regardless of the passed subject.)

User user = ...;

// This is the easy way...
ImmutableContextSet contextSet = user.getQueryOptions().context();
QueryOptions queryOptions = user.getQueryOptions();

// But is equivalent to this...
ContextManager cm = luckPerms.getContextManager();
ImmutableContextSet contextSet = cm.getContext(user).orElse(cm.getStaticContext());
QueryOptions queryOptions = cm.getQueryOptions(user).orElse(cm.getStaticQueryOptions());
The basics of CachedData
All Users and Groups also have an extra object attached to them called CachedData. This is the name of the caching class used by LuckPerms to store easily query-able data for all permission holders. The lookup methods provided by this class are very fast. If you're doing frequent data lookups, it is highly recommended that you use CachedData over the methods in User and Group.

Everything in CachedData is indexed by QueryOptions, as this is how LuckPerms processes all lookups internally.

The contained data is split into two separate sections: CachedPermissionData and CachedMetaData.

CachedPermissionData contains the user/groups fully resolved map of permissions, and allows you to run permission checks in exactly the same way as you would using the Player class provided by the platform.
CachedMetaData contains information about a user/groups prefixes, suffixes, and meta values.
Obtaining CachedPermissionData and CachedMetaData
You need either:

A platform Player instance
A LuckPerms User or Group instance + optionally some QueryOptions (see above for how to obtain this)
If you have a Player platform instance (like org.bukkit.entity.Player), you can use the PlayerAdapter to obtain cached data.

Player player = ...;
PlayerAdapter<Player> adapter = luckperms.getPlayerAdapter(Player.class);

CachedPermissionData permissionData = adapter.getPermissionData(player);
CachedMetaData metaData = adapter.getMetaData(player);
If you already have a LuckPerms User or Group instance, you can use the following methods to obtain cached data.

// Will attempt to use the most appropriate currect query options for the User
CachedPermissionData permissionData = user.getCachedData().getPermissionData();
CachedMetaData metaData = user.getCachedData().getMetaData();

// You can also manually specify which query options to use
CachedPermissionData permissionData = user.getCachedData().getPermissionData(queryOptions);
CachedMetaData metaData = user.getCachedData().getMetaData(queryOptions);
Once you have a cached data instance, you can perform lots of different queries.

Performing permission checks
// run a permission check!
Tristate checkResult = permissionData.checkPermission("some.permission.node");

// the same as what Player#hasPermission would return
boolean checkResultAsBoolean = checkResult.asBoolean();
We can put all of this together to create a method that can run a "normal" permission check when passed a User and a String (the permission).

public boolean hasPermission(User user, String permission) {
    return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
}
Retrieving prefixes/suffixes
String prefix = user.getCachedData().getMetaData().getPrefix();
String suffix = user.getCachedData().getMetaData().getSuffix();
Retrieving metadata
String metaValue = user.getCachedData().getMetaData().getMetaValue("some-key");
These methods work with Groups too!

Store and query custom metadata
The metadata stored by LuckPerms isn't limited to only a few types. You use the API to easily store any sort of data about players, whilst also taking advantage of the storage / caching systems built into LP.

Setting metadata
You can set metadata by creating & adding a MetaNode to a user.

To illustrate this, let's store a player "level" meta value.

public void setLevel(Player player, int level) {
    // obtain a User instance (by any means! see above for other ways)
    User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);

    // create a new MetaNode holding the level value
    // of course, this can have context/expiry/etc too!
    MetaNode node = MetaNode.builder("level", Integer.toString(level)).build();

    // clear any existing meta nodes with the same key - we want to override
    user.data().clear(NodeType.META.predicate(mn -> mn.getMetaKey().equals("level")));
    // add the new node
    user.data().add(node);

    // save!
    luckPerms.getUserManager().saveUser(user);
}
Querying metadata
Once the metadata is set, querying it is easy!

public int getLevel(Player player) {
    // obtain CachedMetaData - the easiest way is via the PlayerAdapter
    // of course, you can get it via a User too if the player is offline.
    CachedMetaData metaData = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);

    // query & parse the meta value
    return metaData.getMetaValue("level", Integer::parseInt).orElse(0);
}
Events
LuckPerms uses it's own event system, completely separate from the event systems used by platforms (e.g. Bukkit or Sponge). This means that instead of registering your listener with the server, you must register it directly with LuckPerms.

The events supported by LuckPerms are defined as interfaces that extend from LuckPermsEvent. They can be found in the net.luckperms.api.event package.

Event listeners
To listen to events, you first need to obtain the EventBus instance using LuckPerms#getEventBus, then register each listener using the subscribe method.

The subscribe method accepts a java.util.function.Consumer object - which allows listeners to be defined as:

Expression lambdas
Statement lambdas
Method references
It's usually a good idea to create a separate class for your listeners. Here's a short example class demonstrating how to subscribe to events.

import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.log.LogPublishEvent;
import net.luckperms.api.event.user.UserLoadEvent;
import net.luckperms.api.event.user.track.UserPromoteEvent;

public class MyListener {
    private final MyPlugin plugin;

    public MyListener(MyPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;

        EventBus eventBus = luckPerms.getEventBus();

        // 1. Subscribe to an event using an expression lambda
        eventBus.subscribe(this.plugin, LogPublishEvent.class, e -> /* ... */);

      	// 2. Subscribe to an event using a statement lambda
        eventBus.subscribe(this.plugin, UserLoadEvent.class, e -> {
            // ...
        });

        // 3. Subscribe to an event using a method reference
        eventBus.subscribe(this.plugin, UserPromoteEvent.class, this::onUserPromote);
    }

    private void onUserPromote(UserPromoteEvent event) {
        // ...
    }
}
If your listener is simple, then an expression or statement lambda is best. If your listener is complex, then method references are probably going to be more organised.

Listening for changes to user cached data
If you have a system that depends on a users cached data (e.g. their prefix or permission state), then you may find it necessary to perform some action in your plugin when the data changes (e.g. invalidate or update a cache). The best & most simple event to use to achieve this is the UserDataRecalculateEvent.

This is a simple event that is "called when a User's cached data is refreshed". It doesn't give any information about what caused the refresh - just that it happened!

Listening for changes to permissions/parent groups/etc
Recall from earlier that all user/group data is stored as Nodes - introducing:

the NodeAddEvent - called when a node is added to a user/group
the NodeRemoveEvent - called when a node is removed from a user/group
the NodeClearEvent - called when a user/group has all/some their existing nodes removed
All of these events extend from NodeMutateEvent which defines the base properties.

These events cover all possible changes that could be made to a user/groups LuckPerms data. The trick is to figure out which event you need, and how to filter down to only catch the desired changes.

For example, to catch prefixes being added to groups, you would need to listen to the NodeAddEvent, then check if e.isGroup() && e.getNode().getType() == NodeType.PREFIX. Of course, afterwards, you could then cast ((Group) e.getTarget()) and ((PrefixNode) node) to extract further information.

To catch both additions and removals, you can either subscribe to the generic NodeMutateEvent, or to both the add and remove events separately.

There is an example listener in the API Cookbook which demonstrates this nicely.


Standalone and REST API
As well as running as a plugin or mod within a Minecraft server, LuckPerms can also be ran as a standalone app. This has two main (current) purposes:

To enable server admins to quickly spin up a LuckPerms CLI and run commands against a remote database, without needing to start a Minecraft server too!
To enable small, standalone extensions (like the REST API) to run as a separate app.
LuckPerms as a Standalone App
The standalone "plugin" is part of the main LuckPerms codebase, within a module called standalone.

The easiest (and recommended) way to run the standalone app is using Docker.

You just need to ensure Docker is installed on your machine, and run the following command!

docker run -it --rm ghcr.io/luckperms/luckperms
This will give you a CLI that you can use just like you would a Minecraft server.



You can configure your database host/user/password using environment variables.

You can also use Docker Compose. Create a file called docker-compose.yml:

version: "3.8"
services:
  luckperms:
    image: ghcr.io/luckperms/luckperms
    # Uncomment if your database is running on the same host
    #extra_hosts:
    #  - "database:host-gateway"
    environment:
      LUCKPERMS_STORAGE_METHOD: mysql
      LUCKPERMS_DATA_ADDRESS: database:3306
      LUCKPERMS_DATA_DATABASE: minecraft
      LUCKPERMS_DATA_USERNAME: root
      LUCKPERMS_DATA_PASSWORD: passw0rd
Start the app using: docker compose up -d.
View the console using: docker compose logs -f luckperms
Send a command using: docker compose exec luckperms send <command>
Stop the app using: docker compose down
LuckPerms REST API (for developers)
The LuckPerms REST API is an "extension" that can run within the standalone app (described above).

It allows other programs, applications or scripts to easily read/modify/write LuckPerms data, without needing to interact with the database directly.

For more information, and instructions for how to use the API, please see:

The LuckPerms/rest-api GitHub repository & readme
The API Specification
Extensions within the Standalone App (for developers)
You can also create your own "plugin-like" Java extensions for the standalone app and have them loaded at startup!

It's quite easy:

Create a file called extension.json at the root of your jar with the following contents:
{"class": "com.example.extension.MainClass"}
(replace with your main class!)
Create a main class that extends import net.luckperms.api.extension.Extension. It should have a no-args constructor, or a constructor that just accepts a net.luckperms.api.LuckPerms instance.
That's it! Add your jar to the extensions folder.

