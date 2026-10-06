package ru.kittenworld.auction;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AuctionPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    static final LegacyComponentSerializer LEG = LegacyComponentSerializer.legacyAmpersand();
    static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    static final String PREFIX = "&6Аукцион &8» &f";
    static final String[] SORTS = {"Сначала новые", "Сначала дешёвые", "Сначала дорогие"};

    enum Type { MAIN, CONFIRM, MY, EXP }

    static final class Listing {
        final int id; final UUID seller; final String sellerName; final double price; final long time; final ItemStack item;
        Listing(int id, UUID seller, String sellerName, double price, long time, ItemStack item) {
            this.id = id; this.seller = seller; this.sellerName = sellerName; this.price = price; this.time = time; this.item = item;
        }
    }

    static final class Holder implements InventoryHolder {
        final Type type;
        int page, sort;
        String query;
        List<Listing> list = new ArrayList<>();
        Listing target;
        Inventory inv;
        Holder(Type t) { this.type = t; }
        @Override public Inventory getInventory() { return inv; }
    }

    private Economy eco;
    private final Map<Integer, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> expired = new HashMap<>();
    private final Set<UUID> searching = new HashSet<>();
    private int nextId = 1;
    private boolean dirty;
    private File dataFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        load();
        getCommand("ah").setExecutor(this);
        getCommand("ah").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1200L, 1200L);
        getServer().getScheduler().runTask(this, () -> {
            if (eco() == null) getLogger().severe("Экономика не найдена! Нужен Vault + плагин экономики (например EssentialsX).");
        });
    }

    @Override
    public void onDisable() { save(); }

    // ---------------- economy / utils ----------------

    private Economy eco() {
        if (eco == null) {
            RegisteredServiceProvider<Economy> r = getServer().getServicesManager().getRegistration(Economy.class);
            if (r != null) eco = r.getProvider();
        }
        return eco;
    }

    static Component c(String s) { return LEG.deserialize(s).decoration(TextDecoration.ITALIC, false); }

    private void msg(Player p, String s) { p.sendMessage(LEG.deserialize(PREFIX + s)); }

    private String fmt(double d) {
        String cur = getConfig().getString("currency", "$");
        String n = d == Math.floor(d) ? String.format(Locale.US, "%,d", (long) d) : String.format(Locale.US, "%,.2f", d);
        return n.replace(',', ' ') + cur;
    }

    private String left(Listing l) {
        long ms = getConfig().getLong("expire-hours", 48) * 3600_000L - (System.currentTimeMillis() - l.time);
        if (ms < 0) ms = 0;
        return (ms / 3600_000L) + "ч " + ((ms / 60_000L) % 60) + "м";
    }

    private static double parsePrice(String s) {
        s = s.toLowerCase().replace(',', '.');
        double m = 1;
        if (s.endsWith("кк") || s.endsWith("kk") || s.endsWith("m") || s.endsWith("м")) {
            m = 1_000_000;
            s = s.replaceAll("(кк|kk|m|м)$", "");
        } else if (s.endsWith("к") || s.endsWith("k")) {
            m = 1000;
            s = s.substring(0, s.length() - 1);
        }
        try { return Double.parseDouble(s) * m; } catch (Exception e) { return -1; }
    }

    private static String enc(ItemStack i) { return Base64.getEncoder().encodeToString(i.serializeAsBytes()); }
    private static ItemStack dec(String s) { return ItemStack.deserializeBytes(Base64.getDecoder().decode(s)); }

    private void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItem(p.getLocation(), rest));
    }

    private void later(Runnable r) { Bukkit.getScheduler().runTask(this, r); }

    // ---------------- storage ----------------

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        nextId = y.getInt("next", 1);
        ConfigurationSection s = y.getConfigurationSection("listings");
        if (s != null) for (String k : s.getKeys(false)) {
            ConfigurationSection l = s.getConfigurationSection(k);
            if (l == null) continue;
            try {
                listings.put(Integer.parseInt(k), new Listing(Integer.parseInt(k), UUID.fromString(l.getString("seller")),
                        l.getString("name", "?"), l.getDouble("price"), l.getLong("time"), dec(l.getString("item"))));
            } catch (Exception ex) { getLogger().warning("Не удалось загрузить лот " + k); }
        }
        ConfigurationSection e = y.getConfigurationSection("expired");
        if (e != null) for (String k : e.getKeys(false)) {
            List<ItemStack> list = new ArrayList<>();
            for (String b : e.getStringList(k)) {
                try { list.add(dec(b)); } catch (Exception ignored) { }
            }
            expired.put(UUID.fromString(k), list);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next", nextId);
        for (Listing l : listings.values()) {
            String b = "listings." + l.id + ".";
            y.set(b + "seller", l.seller.toString());
            y.set(b + "name", l.sellerName);
            y.set(b + "price", l.price);
            y.set(b + "time", l.time);
            y.set(b + "item", enc(l.item));
        }
        for (Map.Entry<UUID, List<ItemStack>> en : expired.entrySet()) {
            List<String> out = new ArrayList<>();
            for (ItemStack i : en.getValue()) out.add(enc(i));
            y.set("expired." + en.getKey(), out);
        }
        try { y.save(dataFile); } catch (Exception ex) { getLogger().severe("Ошибка сохранения: " + ex.getMessage()); }
        dirty = false;
    }

    private void tick() {
        long ttl = getConfig().getLong("expire-hours", 48) * 3600_000L, now = System.currentTimeMillis();
        for (Listing l : new ArrayList<>(listings.values())) {
            if (now - l.time > ttl) {
                listings.remove(l.id);
                expired.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(l.item);
                dirty = true;
                Player p = Bukkit.getPlayer(l.seller);
                if (p != null) msg(p, "&eВаш лот истёк. Заберите предмет: &f/ah &e→ Хранилище");
            }
        }
        if (dirty) save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        List<ItemStack> l = expired.get(p.getUniqueId());
        if (l != null && !l.isEmpty()) later(() -> msg(p, "&eУ вас есть предметы в хранилище аукциона: &f/ah"));
    }

    // ---------------- commands ----------------

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Только для игроков"); return true; }
        if (a.length == 0) { openMain(p, null, 0, 0); return true; }
        switch (a[0].toLowerCase()) {
            case "sell", "продать" -> sell(p, a);
            case "search", "поиск" -> openMain(p, a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : null, 0, 0);
            case "reload" -> {
                if (p.hasPermission("kittenworldauction.admin")) { reloadConfig(); msg(p, "&aКонфиг перезагружен."); }
            }
            default -> openMain(p, null, 0, 0);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("sell", "search");
        return List.of();
    }

    private void sell(Player p, String[] a) {
        if (a.length < 2) { msg(p, "&cИспользование: &f/ah sell <цена> &7(можно 5к, 2кк)"); return; }
        double price = parsePrice(a[1]);
        double min = getConfig().getDouble("min-price", 1), max = getConfig().getDouble("max-price", 1e12);
        if (price < min || price > max) { msg(p, "&cЦена должна быть от " + fmt(min) + " до " + fmt(max)); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { msg(p, "&cВозьмите предмет в руку."); return; }
        if (getConfig().getStringList("blacklist").contains(hand.getType().name())) { msg(p, "&cЭтот предмет нельзя продавать."); return; }
        long mine = listings.values().stream().filter(l -> l.seller.equals(p.getUniqueId())).count();
        if (mine >= getConfig().getInt("max-listings", 10)) { msg(p, "&cДостигнут лимит лотов."); return; }
        Listing l = new Listing(nextId++, p.getUniqueId(), p.getName(), price, System.currentTimeMillis(), hand.clone());
        listings.put(l.id, l);
        p.getInventory().setItemInMainHand(null);
        dirty = true;
        msg(p, "&aПредмет выставлен за &e" + fmt(price));
    }

    // ---------------- menus ----------------

    private ItemStack icon(Material m, String name, String... lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta mt = i.getItemMeta();
        mt.displayName(c(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            mt.lore(l);
        }
        i.setItemMeta(mt);
        return i;
    }

    private ItemStack view(Listing l, Player viewer) {
        ItemStack it = l.item.clone();
        List<Component> lore = it.lore() != null ? new ArrayList<>(it.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(c("&7Продавец: &f" + l.sellerName));
        lore.add(c("&7Цена: &a" + fmt(l.price)));
        lore.add(c("&7Осталось: &f" + left(l)));
        lore.add(Component.empty());
        lore.add(c(l.seller.equals(viewer.getUniqueId()) ? "&eНажмите, чтобы снять с продажи" : "&aНажмите, чтобы купить"));
        it.lore(lore);
        return it;
    }

    private void bottom(Inventory inv, Holder h, int pages) {
        ItemStack g = icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) inv.setItem(i, g);
        if (h.page > 0) inv.setItem(45, icon(Material.ARROW, "&e← Назад", "&7Страница &f" + (h.page + 1) + "&7/&f" + pages));
        if (h.page < pages - 1) inv.setItem(53, icon(Material.ARROW, "&eВперёд →", "&7Страница &f" + (h.page + 1) + "&7/&f" + pages));
        if (h.type == Type.MAIN) {
            inv.setItem(46, icon(Material.HOPPER, "&6Сортировка", "&7Сейчас: &f" + SORTS[h.sort], "&eНажмите, чтобы сменить"));
            inv.setItem(48, icon(Material.OAK_SIGN, "&bПоиск", "&7Найти предмет по названию", "&7(на русском или английском)",
                    h.query == null ? "" : "&7Запрос: &f" + h.query));
            inv.setItem(49, icon(Material.CHEST, "&aМои лоты", "&7Ваши предметы на продаже"));
            inv.setItem(50, icon(Material.ENDER_CHEST, "&dХранилище", "&7Истёкшие и возвращённые предметы"));
            if (h.query != null) inv.setItem(52, icon(Material.BARRIER, "&cСбросить поиск"));
        } else {
            inv.setItem(49, icon(Material.BARRIER, "&cНазад в аукцион"));
        }
    }

    private static int pagesOf(int size) { return Math.max(1, (size + 44) / 45); }

    void openMain(Player p, String query, int sort, int page) {
        Holder h = new Holder(Type.MAIN);
        h.query = query == null || query.isBlank() ? null : query;
        h.sort = sort;
        for (Listing l : listings.values()) if (h.query == null || Names.matches(l.item, h.query)) h.list.add(l);
        Comparator<Listing> cmp = switch (sort) {
            case 1 -> Comparator.comparingDouble(l -> l.price);
            case 2 -> Comparator.<Listing>comparingDouble(l -> l.price).reversed();
            default -> Comparator.<Listing>comparingLong(l -> l.time).reversed();
        };
        h.list.sort(cmp);
        int pages = pagesOf(h.list.size());
        h.page = Math.max(0, Math.min(page, pages - 1));
        String title = getConfig().getString("title", "&8Аукцион") + (h.query != null ? " &8| &7" + h.query : "");
        h.inv = Bukkit.createInventory(h, 54, c(title));
        for (int i = 0; i < 45; i++) {
            int idx = h.page * 45 + i;
            if (idx < h.list.size()) h.inv.setItem(i, view(h.list.get(idx), p));
        }
        bottom(h.inv, h, pages);
        p.openInventory(h.inv);
    }

    private void openMy(Player p, int page) {
        Holder h = new Holder(Type.MY);
        for (Listing l : listings.values()) if (l.seller.equals(p.getUniqueId())) h.list.add(l);
        int pages = pagesOf(h.list.size());
        h.page = Math.max(0, Math.min(page, pages - 1));
        h.inv = Bukkit.createInventory(h, 54, c("&8Мои лоты"));
        for (int i = 0; i < 45; i++) {
            int idx = h.page * 45 + i;
            if (idx < h.list.size()) h.inv.setItem(i, view(h.list.get(idx), p));
        }
        bottom(h.inv, h, pages);
        p.openInventory(h.inv);
    }

    private void openExp(Player p, int page) {
        Holder h = new Holder(Type.EXP);
        List<ItemStack> items = expired.getOrDefault(p.getUniqueId(), new ArrayList<>());
        int pages = pagesOf(items.size());
        h.page = Math.max(0, Math.min(page, pages - 1));
        h.inv = Bukkit.createInventory(h, 54, c("&8Хранилище"));
        for (int i = 0; i < 45; i++) {
            int idx = h.page * 45 + i;
            if (idx < items.size()) {
                ItemStack it = items.get(idx).clone();
                List<Component> lore = it.lore() != null ? new ArrayList<>(it.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(c("&aНажмите, чтобы забрать"));
                it.lore(lore);
                h.inv.setItem(i, it);
            }
        }
        bottom(h.inv, h, pages);
        p.openInventory(h.inv);
    }

    private void openConfirm(Player p, Holder from, Listing l) {
        Holder h = new Holder(Type.CONFIRM);
        h.target = l; h.query = from.query; h.sort = from.sort; h.page = from.page;
        h.inv = Bukkit.createInventory(h, 27, c("&8Подтверждение покупки"));
        ItemStack g = icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) h.inv.setItem(i, g);
        h.inv.setItem(13, view(l, p));
        h.inv.setItem(11, icon(Material.LIME_CONCRETE, "&a&lКупить", "&7Цена: &a" + fmt(l.price)));
        h.inv.setItem(15, icon(Material.RED_CONCRETE, "&c&lОтмена"));
        p.openInventory(h.inv);
    }

    // ---------------- clicks ----------------

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        int s = e.getRawSlot();
        switch (h.type) {
            case MAIN -> clickMain(p, h, s);
            case CONFIRM -> clickConfirm(p, h, s);
            case MY -> clickMy(p, h, s);
            case EXP -> clickExp(p, h, s);
        }
    }

    private void clickMain(Player p, Holder h, int s) {
        int pages = pagesOf(h.list.size());
        if (s < 45) {
            int idx = h.page * 45 + s;
            if (idx >= h.list.size()) return;
            Listing l = h.list.get(idx);
            if (!listings.containsKey(l.id)) {
                msg(p, "&cЛот уже недоступен.");
                later(() -> openMain(p, h.query, h.sort, h.page));
                return;
            }
            if (l.seller.equals(p.getUniqueId())) {
                listings.remove(l.id); dirty = true;
                give(p, l.item.clone());
                msg(p, "&eЛот снят с продажи.");
                later(() -> openMain(p, h.query, h.sort, h.page));
            } else {
                later(() -> openConfirm(p, h, l));
            }
        } else if (s == 45 && h.page > 0) {
            later(() -> openMain(p, h.query, h.sort, h.page - 1));
        } else if (s == 53 && h.page < pages - 1) {
            later(() -> openMain(p, h.query, h.sort, h.page + 1));
        } else if (s == 46) {
            later(() -> openMain(p, h.query, (h.sort + 1) % SORTS.length, 0));
        } else if (s == 48) {
            p.closeInventory();
            searching.add(p.getUniqueId());
            msg(p, "&bВведите в чат название предмета (рус/eng). Для отмены напишите &fотмена");
        } else if (s == 49) {
            later(() -> openMy(p, 0));
        } else if (s == 50) {
            later(() -> openExp(p, 0));
        } else if (s == 52 && h.query != null) {
            later(() -> openMain(p, null, h.sort, 0));
        }
    }

    private void clickConfirm(Player p, Holder h, int s) {
        if (s == 11) {
            buy(p, h.target);
            later(() -> openMain(p, h.query, h.sort, h.page));
        } else if (s == 15) {
            later(() -> openMain(p, h.query, h.sort, h.page));
        }
    }

    private void clickMy(Player p, Holder h, int s) {
        int pages = pagesOf(h.list.size());
        if (s < 45) {
            int idx = h.page * 45 + s;
            if (idx >= h.list.size()) return;
            Listing l = h.list.get(idx);
            if (listings.remove(l.id) != null) {
                dirty = true;
                give(p, l.item.clone());
                msg(p, "&eЛот снят с продажи.");
            }
            later(() -> openMy(p, h.page));
        } else if (s == 45 && h.page > 0) later(() -> openMy(p, h.page - 1));
        else if (s == 53 && h.page < pages - 1) later(() -> openMy(p, h.page + 1));
        else if (s == 49) later(() -> openMain(p, null, 0, 0));
    }

    private void clickExp(Player p, Holder h, int s) {
        List<ItemStack> items = expired.getOrDefault(p.getUniqueId(), new ArrayList<>());
        int pages = pagesOf(items.size());
        if (s < 45) {
            int idx = h.page * 45 + s;
            if (idx >= items.size()) return;
            ItemStack it = items.remove(idx);
            dirty = true;
            give(p, it);
            later(() -> openExp(p, h.page));
        } else if (s == 45 && h.page > 0) later(() -> openExp(p, h.page - 1));
        else if (s == 53 && h.page < pages - 1) later(() -> openExp(p, h.page + 1));
        else if (s == 49) later(() -> openMain(p, null, 0, 0));
    }

    private void buy(Player p, Listing l) {
        Economy e = eco();
        if (e == null) { msg(p, "&cЭкономика недоступна."); return; }
        if (!listings.containsKey(l.id)) { msg(p, "&cЛот уже продан или снят."); return; }
        if (e.getBalance(p) < l.price) { msg(p, "&cНедостаточно средств."); return; }
        EconomyResponse r = e.withdrawPlayer(p, l.price);
        if (!r.transactionSuccess()) { msg(p, "&cОшибка оплаты."); return; }
        double tax = l.price * getConfig().getDouble("tax-percent", 5) / 100.0;
        e.depositPlayer(Bukkit.getOfflinePlayer(l.seller), l.price - tax);
        listings.remove(l.id);
        dirty = true;
        give(p, l.item.clone());
        msg(p, "&aВы купили предмет за &e" + fmt(l.price));
        Player seller = Bukkit.getPlayer(l.seller);
        if (seller != null) msg(seller, "&aИгрок &f" + p.getName() + " &aкупил ваш лот. Получено: &e" + fmt(l.price - tax));
    }

    // ---------------- search input ----------------

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!searching.remove(p.getUniqueId())) return;
        e.setCancelled(true);
        String text = PLAIN.serialize(e.message()).trim();
        later(() -> {
            if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) openMain(p, null, 0, 0);
            else openMain(p, text, 0, 0);
        });
    }
}
