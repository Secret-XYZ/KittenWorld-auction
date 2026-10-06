package ru.exce.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Поиск предметов: английские названия + русский словарь + название/описание кастомных предметов. */
final class Names {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Map<String, String> RU = new HashMap<>();

    static {
        String data = """
            diamond=алмаз алмазный алмазная алмазное алмазные алмазов
            iron=железо железный железная железное железные
            gold=золото золотой золотая золотое золотые золота
            golden=золото золотой золотая золотое золотые золота
            netherite=незерит незеритовый незеритовая незеритовое незеритовые
            copper=медь медный медная медное медные
            stone=камень каменный каменная каменное каменные
            wooden=деревянный деревянная деревянное деревянные
            leather=кожа кожаный кожаная кожаное кожаные
            chainmail=кольчуга кольчужный кольчужная
            sword=меч мечи
            pickaxe=кирка кирки
            axe=топор топоры
            shovel=лопата лопаты
            hoe=мотыга мотыги
            helmet=шлем шлемы
            chestplate=нагрудник нагрудники
            leggings=поножи штаны
            boots=ботинки сапоги
            ingot=слиток слитки
            nugget=самородок самородки
            block=блок блоки
            ore=руда руды
            raw=сырой сырая сырое сырые
            cooked=жареный жареная жареное
            apple=яблоко яблоки
            bow=лук
            crossbow=арбалет
            arrow=стрела стрелы
            shield=щит
            elytra=элитры крылья
            totem=тотем
            undying=бессмертия
            ender=эндер края
            pearl=жемчуг
            eye=око глаз
            stick=палка палки
            coal=уголь
            charcoal=древесный уголь
            redstone=редстоун красная пыль
            lapis=лазурит
            lazuli=лазурит
            emerald=изумруд изумруды
            quartz=кварц
            glowstone=светокамень
            obsidian=обсидиан
            crying=плачущий
            beacon=маяк
            shulker=шалкер шулкер
            box=ящик коробка
            chest=сундук
            barrel=бочка
            potion=зелье зелья
            splash=взрывное
            lingering=туманное
            book=книга
            enchanted=зачарованный зачарованная зачарованное
            enchanting=зачарования
            table=стол
            bottle=бутылка пузырек
            experience=опыт опыта
            spawner=спавнер
            spawn=спавн
            egg=яйцо
            head=голова
            skull=череп
            wheat=пшеница
            bread=хлеб
            carrot=морковь морковка
            potato=картофель картошка
            beetroot=свекла
            beef=говядина
            steak=стейк
            porkchop=свинина
            chicken=курица
            mutton=баранина
            rabbit=кролик
            cod=треска
            salmon=лосось
            fish=рыба
            log=бревно бревна
            planks=доски
            oak=дуб дубовый
            spruce=ель еловый
            birch=береза березовый
            jungle=джунгли тропический
            acacia=акация
            dark=темный
            cherry=вишня вишневый
            mangrove=мангровый
            bamboo=бамбук
            dirt=земля грязь
            grass=трава
            sand=песок
            gravel=гравий
            glass=стекло
            wool=шерсть
            bed=кровать
            torch=факел
            lava=лава
            water=вода
            bucket=ведро
            compass=компас
            clock=часы
            map=карта
            lead=поводок
            saddle=седло
            name=имя
            tag=бирка
            trident=трезубец
            mace=булава
            wind=ветер ветряной
            charge=заряд
            netherrack=незерак
            nether=незер
            soul=души
            blaze=ифрит
            rod=стержень
            powder=порошок
            gunpowder=порох
            tnt=тнт динамит
            flint=кремень
            feather=перо
            string=нить
            bone=кость
            meal=мука
            slime=слизь
            ball=шар комок
            spider=паук
            ghast=гаст
            tear=слеза
            fire=огонь
            fireworks=фейерверк
            firework=фейерверк
            rocket=ракета
            star=звезда
            dragon=дракон
            breath=дыхание
            heart=сердце
            sea=море
            nautilus=наутилус
            shell=панцирь раковина
            scute=щиток
            turtle=черепаха
            phantom=фантом
            membrane=перепонка
            foot=лапка
            hide=шкура
            honey=мед
            honeycomb=соты
            cake=торт
            cookie=печенье
            pie=пирог
            pumpkin=тыква
            melon=арбуз дыня
            sugar=сахар
            cane=тростник
            cocoa=какао
            beans=бобы
            berries=ягоды
            glow=светящийся
            kelp=ламинария
            cactus=кактус
            vine=лоза
            lily=кувшинка
            sapling=саженец
            seeds=семена
            disc=пластинка
            dye=краситель
            banner=флаг баннер
            armor=броня
            trim=отделка
            template=шаблон
            smithing=кузнечный
            anvil=наковальня
            furnace=печь
            blast=плавильная
            smoker=коптильня
            hopper=воронка
            dropper=выбрасыватель
            dispenser=раздатчик
            piston=поршень
            sticky=липкий
            observer=наблюдатель
            repeater=повторитель
            comparator=компаратор
            lever=рычаг
            button=кнопка
            pressure=нажимная
            plate=плита
            rail=рельсы рельс
            minecart=вагонетка
            boat=лодка
            door=дверь
            trapdoor=люк
            fence=забор
            gate=калитка
            stairs=ступени лестница
            slab=плита полублок
            wall=стена
            brick=кирпич
            bricks=кирпичи
            terracotta=терракота
            concrete=бетон
            clay=глина
            prismarine=призмарин
            purpur=пурпур
            end=энд края
            crimson=багровый
            warped=искаженный
            mushroom=гриб
            amethyst=аметист
            shard=осколок
            spyglass=подзорная труба
            brush=кисть
            sculk=скалк
            ancient=древний
            debris=обломки
            scrap=обломки
            lodestone=магнетит
            respawn=возрождения
            anchor=якорь
            conduit=проводник
            bookshelf=книжная полка
            crafting=верстак
            white=белый
            orange=оранжевый
            magenta=пурпурный
            light=светлый
            blue=синий голубой
            yellow=желтый
            lime=лаймовый
            pink=розовый
            gray=серый
            cyan=бирюзовый
            purple=фиолетовый
            brown=коричневый
            green=зеленый
            red=красный
            black=черный
            """;
        for (String line : data.split("\n")) {
            int i = line.indexOf('=');
            if (i > 0) RU.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
        }
    }

    static String norm(String s) {
        return s.toLowerCase().replace('ё', 'е').replace('_', ' ').replaceAll("§.", "").trim();
    }

    private static String plain(Component c) {
        return PLAIN.serialize(c);
    }

    static String haystack(ItemStack it) {
        StringBuilder sb = new StringBuilder();
        String key = it.getType().name().toLowerCase();
        sb.append(key.replace('_', ' ')).append(' ');
        for (String w : key.split("_")) {
            String ru = RU.get(w);
            if (ru != null) sb.append(ru).append(' ');
        }
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            if (m.hasDisplayName() && m.displayName() != null) sb.append(plain(m.displayName())).append(' ');
            if (m.hasLore()) {
                List<Component> lore = m.lore();
                if (lore != null) for (Component c : lore) sb.append(plain(c)).append(' ');
            }
        }
        return norm(sb.toString());
    }

    static boolean matches(ItemStack it, String query) {
        String hay = haystack(it);
        for (String t : norm(query).split("\\s+")) {
            if (!t.isEmpty() && !hay.contains(t)) return false;
        }
        return true;
    }

    private Names() {}
}
