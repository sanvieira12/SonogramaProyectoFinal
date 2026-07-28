package com.sonograma.service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Official DAC agency catalog captured from https://www.dac.com.uy/agencias.
 * The numeric values are DAC's stable agency identifiers from data-k-oficina;
 * the public application id is prefixed to keep it unambiguous.
 */
public final class DacBranchCatalog {

    public record Branch(String id, String codigo, String departamento, String nombre, String direccion) {
        public String label() {
            return nombre + " — " + direccion;
        }
    }

    public static final List<String> DEPARTAMENTOS = List.of(
            "Artigas", "Canelones", "Cerro Largo", "Colonia", "Durazno", "Flores",
            "Florida", "Lavalleja", "Maldonado", "Montevideo", "Paysandú", "Río Negro",
            "Rivera", "Rocha", "Salto", "San José", "Soriano", "Tacuarembó", "Treinta y Tres"
    );

    private static final List<Branch> BRANCHES = List.of(
            branch("620", "Artigas", "Artigas", "Luis Alberto De Herrera – Terminal"),
            branch("144", "Artigas", "Deposito Artigas", "Mauro Garcia Da Rosa 289"),
            branch("610", "Artigas", "Bella Union", "General Fructoso Rivera 399"),
            branch("894", "Canelones", "Barros Blancos", "Ruta 8 Km 26 - Calle Lateral"),
            branch("719", "Canelones", "Canelones", "Treinta Y Tres Esq. Acuña De Figueroa"),
            branch("735", "Canelones", "Ciudad De La Costa Shangrila", "Av. Giannattasio M8 S5 Esq. Cuba Y Chile"),
            branch("845", "Canelones", "El Pinar", "Avda. Giannastasio Km 28 500 Manzana J1 Solar 4"),
            branch("178", "Canelones", "La Paz", "Bv. Artigas 386"),
            branch("619", "Canelones", "Las Piedras", "Dr. Pouey"),
            branch("878", "Canelones", "Migues", "Dr. Luis Alberto De Herrera Esq. G. Migues 1717"),
            branch("740", "Canelones", "Pando", "Av. Roosevelt 1118 Ruta 8 Km 31"),
            branch("856", "Canelones", "Parque Del Plata", "Calle 9 Y Ruta Interbalnearia, Manzana 603, Solar 8, Local 1"),
            branch("726", "Canelones", "Progreso", "Av. Artigas Esq. Florida"),
            branch("174", "Canelones", "Zona Progreso", "Ruta 5 Y Av. Wilson Ferreira Adunate"),
            branch("857", "Canelones", "Salinas", "Av. Zorrilla De San Martín. Manzana 87, Solar 1. Entre Calandria Y Solís."),
            branch("729", "Canelones", "San Jacinto", "Maria Viera E/calletano Gonzalez Y Av. Artigas"),
            branch("888", "Canelones", "San Luis", "Ruta Interbalnearia Km 63.200 Esq. La Paz"),
            branch("105", "Canelones", "Ancap San Ramon", "Av. Jose Batlle Y Ordóñez 1805 – Estación Ancap"),
            branch("723", "Canelones", "Santa Lucia", "Dr. Alejandro Legnani 551"),
            branch("730", "Canelones", "Tala", "18 De Julio Esq. Ildefonzo"),
            branch("836", "Canelones", "Toledo", "Batlle Y Ordoñez 204"),
            branch("921", "Cerro Largo", "Ancap Fraile Muerto", "Ruta 7 Kmt 357 Y Ruta 44 – Estación Ancap"),
            branch("792", "Cerro Largo", "Melo", "Doroteo Navarrete 865"),
            branch("680", "Cerro Largo", "Rio Branco", "Virrey Arredondo 1263"),
            branch("616", "Colonia", "Carmelo", "18 De Julio 411"),
            branch("617", "Colonia", "Colonia", "Franklin D. Roosevelt 458"),
            branch("904", "Colonia", "Colonia Terminal", "Av. Roosevelt – Terminal"),
            branch("637", "Colonia", "Miguelete", "Av. Artigas Esq. Juan Carlos Curbelo"),
            branch("641", "Colonia", "Valdense", "Ruta 1 Y Av. General Artigas"),
            branch("744", "Colonia", "Conchillas", "Extension Ruta 55 Km 5 Y La Palmita – Estación Ancap"),
            branch("690", "Colonia", "Juan Lacaze", "Jose Salvo S/n"),
            branch("881", "Colonia", "Nueva Helvecia", "Camino De Los Colonos Esq. German Imhoff"),
            branch("615", "Colonia", "Nueva Palmira", "Santiago De Chile 1166"),
            branch("636", "Colonia", "Ombues De Lavalle", "Colonia 758"),
            branch("628", "Colonia", "Rosario", "Rincon Y Sarandi"),
            branch("635", "Colonia", "Tarariras", "18 De Julio 1797"),
            branch("948", "Durazno", "Ancap Durazno", "19 De Abril Y Baltazar Brum"),
            branch("897", "Durazno", "Deposito Durazno", "Joaquin Suarez 169"),
            branch("618", "Durazno", "Durazno", "Terminal"),
            branch("936", "Durazno", "Ancap La Paloma (durazno)", "Celia Galarza – Estación Ancap"),
            branch("880", "Durazno", "Sarandi Del Yi", "Bernadet 537"),
            branch("606", "Flores", "Trinidad", "Terminal De Omnibus Guyunusa Locale 2 Y 3"),
            branch("717", "Florida", "25 De Agosto", "Joaquín Suarez 328"),
            branch("718", "Florida", "25 De Mayo", "Artigas 420"),
            branch("720", "Florida", "Cardal", "Av. Artigas 1037"),
            branch("721", "Florida", "Casupa", "Pons Y Juani 1021"),
            branch("639", "Florida", "Terminal Florida", "Terminal Florida"),
            branch("728", "Florida", "Fray Marcos", "Dr. Cyro Giambruno 987"),
            branch("722", "Florida", "Independencia", "Ruta 77 Km 71"),
            branch("691", "Florida", "Sarandi Grande", "Ruta 5 Km 139.100"),
            branch("811", "Lavalleja", "Batlle Y Ordoñez", "18 De Julio 16"),
            branch("676", "Lavalleja", "Agencia Jose Pedro Varela", "Treinta Y Tres 474"),
            branch("683", "Lavalleja", "Mariscala", "Lavalleja Esq. Av. Artigas Y Sarandí"),
            branch("678", "Lavalleja", "Minas", "Intendente Solano Amilivia 395 Esq. Colon"),
            branch("809", "Lavalleja", "Solis De Mataojo", "Ruta 8 Km 81 – Estación Ancap"),
            branch("902", "Maldonado", "Aigua", "18 De Julio 790"),
            branch("137", "Maldonado", "Balneario Buenos Aires", "Calle 49 Y 29"),
            branch("895", "Maldonado", "La Barra", "Ruta 10, Entre Los Destinos Y Serenidad, Manzana 8, Padron 8"),
            branch("952", "Maldonado", "La Capuera", "Ruta Interbalnearia Km 110"),
            branch("708", "Maldonado", "Maldonado", "Santa Teresa 600"),
            branch("173", "Maldonado", "Maldonado Calle Lussich", "Lussich S/n"),
            branch("109", "Maldonado", "Pan De Azucar Dac", "Feliz De Lizarza 688"),
            branch("710", "Maldonado", "Piriapolis", "Zolezzi 842 Esq. Tucuman Y Salta"),
            branch("820", "Maldonado", "Punta Del Este", "Rbla. General Artigas Esq Risso – Frente A Terminal"),
            branch("110", "Maldonado", "San Carlos Dac", "Dr. Andrés Ceberio Esq. Carlos Reyles"),
            branch("185", "Montevideo", "Cerro", "Peru 2068"),
            branch("930", "Montevideo", "Agencia Perimetral Ruta 5", "Ruta 5 Km 16 Esq. Ruta 102"),
            branch("633", "Montevideo", "Av Italia", "Av. Italia 5680"),
            branch("908", "Montevideo", "Ciudad Vieja", "Juan Carlos Gomez 1447"),
            branch("103", "Montevideo", "Funsa", "Camino Corrales 3076"),
            branch("899", "Montevideo", "La Comercial", "Hocquart 1691"),
            branch("892", "Montevideo", "Mercado Modelo", "Bv. Jose Batlle Y Ordoñez Esq. Thompson"),
            branch("915", "Montevideo", "Millan", "Av. Millan 4110"),
            branch("770", "Montevideo", "Paso Molino", "Mariano Sagasta 64"),
            branch("125", "Montevideo", "Pocitos", "Avenida Rivera 3547"),
            branch("601", "Montevideo", "Rondeau", "Rondeau 1475"),
            branch("661", "Montevideo", "Tres Cruces", "Terminal Tres Cruces (subsuelo)"),
            branch("181", "Montevideo", "Belloni - Piedras Blancas", "Belloni 4167 – Local A"),
            branch("907", "Montevideo", "Ancap Sayago", "Camino Ariel 4697"),
            branch("605", "Paysandú", "Guichon", "18 De Julio 611"),
            branch("603", "Paysandú", "Paysandu Herrera", "Dr. Herrera 873"),
            branch("621", "Paysandú", "Terminal Paysandu", "Bv. Artigas 770"),
            branch("654", "Paysandú", "Quebracho", "Macario Garcia 219"),
            branch("807", "Rivera", "Minas De Corrales", "Dr. Davinson S/n. Escritorio Kappas"),
            branch("608", "Rivera", "Rivera", "Terminal Municipal, M. Vera Y"),
            branch("903", "Rivera", "Rivera Deposito", "Uruguay 808"),
            branch("913", "Rivera", "Ancap Tranqueras", "25 De Agosto Y E. Navarro – Estación Ancap"),
            branch("946", "Rivera", "Vichadero", "Bv. General Artigas 1010"),
            branch("112", "Rocha", "Castillos Dac", "Dr. Ferrer 1346"),
            branch("122", "Rocha", "Cebollati", "Rocha S/n Esq. Ruta 15"),
            branch("113", "Rocha", "Chuy", "Leonardo Olivera S/n"),
            branch("115", "Rocha", "La Coronilla", "Estrella De Mar S/n Esq. Las Acacias"),
            branch("119", "Rocha", "La Paloma Rocha", "Antares Entre Canopus Y De La Iglesia"),
            branch("121", "Rocha", "Lascano Dac", "Ituzaingo 1340 Entre Sarandi Y 1ro De Agosto"),
            branch("116", "Rocha", "Punta Del Diablo Dac", "Av. Santa Teresa S/n Esq. Brasil"),
            branch("111", "Rocha", "Rocha Dac", "Jose Batlle Y Ordoñez 362"),
            branch("120", "Rocha", "Velazquez", "General Artigas Y Armando Abdo"),
            branch("612", "Río Negro", "Fray Bentos", "Terminal Municipal"),
            branch("646", "Río Negro", "Nuevo Berlin", "Uruguay"),
            branch("622", "Río Negro", "San Javier", "B. Luvkov C/ Arrechavaleta"),
            branch("604", "Río Negro", "Young", "Av. Zeballos Y V. Nunez (terminal) S/n"),
            branch("602", "Salto", "Salto Cerrito", "Bortagaray ('cerrito') 66"),
            branch("689", "Salto", "Salto Terminal", "Av. Batlle 2265"),
            branch("889", "San José", "Ecilda Paullier-", "Av. General Artigas Y Federico Paullier S/n"),
            branch("715", "San José", "Libertad", "25 De Agosto"),
            branch("731", "San José", "Km 61 Puntas De Valdez", "Ruta 1 Km 61"),
            branch("642", "San José", "Ciudad Del Plata", "Ruta 1 Km 26"),
            branch("898", "San José", "San Jose Centro", "Batlle Y Ordoñez 349"),
            branch("631", "San José", "San Jose", "Terminal De Omnibus"),
            branch("724", "San José", "Villa Rodriguez", "Santiago Rodriguez 876"),
            branch("638", "Soriano", "Cañada Nieto", "Telecentro Antel"),
            branch("629", "Soriano", "Cardona", "Artigas 1351"),
            branch("614", "Soriano", "Dolores", "18 De Julio 1467"),
            branch("630", "Soriano", "Rodo", "Ruta 2 Km 209"),
            branch("950", "Soriano", "Deposito Mercedes", "Eusebio Gimenez 1136 Esq. Tomas Gomez"),
            branch("698", "Soriano", "Terminal Mercedes", "Don Bosco 734 – Terminal"),
            branch("626", "Soriano", "Palmitas", "18 De Julio Y Juana De Ibarbou"),
            branch("688", "Soriano", "Palo Solo", "Ruta 96 Km.63.500"),
            branch("953", "Soriano", "Pueblo Risso", "Calle 6 Entre 11 Y 13"),
            branch("627", "Soriano", "Santa Catalina", "Ruta 2 Km 197"),
            branch("707", "Tacuarembó", "Caraguata", "Terminal"),
            branch("623", "Tacuarembó", "Paso De Los Toros", "Atanasio Sierra 413"),
            branch("712", "Tacuarembó", "San Gregorio", "Av. Arturo Mollo 155"),
            branch("884", "Tacuarembó", "Tacuarembo Deposito", "Ruta 5 Km 387.500"),
            branch("607", "Tacuarembó", "Tacuarembo Terminal", "Terminal Carlos Gardel S/n"),
            branch("142", "Tacuarembó", "Tambores", "Av. Fernandez Lascano"),
            branch("132", "Treinta Y Tres", "Cerro Chato", "Av. Centenario S/n"),
            branch("934", "Treinta Y Tres", "Pueblo Rincon", "Acacias 6698 Esq. José Pedro Varela - Mevir"),
            branch("935", "Treinta Y Tres", "Santa Clara", "25 De Agosto Esq. Modesto Polanco"),
            branch("679", "Treinta Y Tres", "Treinta Y Tres", "Manuel Lavalleja"),
            branch("677", "Treinta Y Tres", "Vergara", "Fortunato Jara 1825")
    );

    private DacBranchCatalog() {}

    public static Optional<Branch> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String normalized = id.trim();
        return BRANCHES.stream()
                .filter(branch -> branch.id().equalsIgnoreCase(normalized)
                        || branch.codigo().equalsIgnoreCase(normalized)
                        || branch.id().equalsIgnoreCase("dac-" + normalized))
                .findFirst();
    }

    public static List<Branch> getByDepartment(String departamento) {
        String wanted = normalizeText(departamento);
        return sort(BRANCHES.stream()
                .filter(branch -> normalizeText(branch.departamento()).equals(wanted))
                .collect(Collectors.toList()));
    }

    public static boolean belongsToDepartment(String branchId, String departamento) {
        return findById(branchId)
                .map(branch -> normalizeText(branch.departamento()).equals(normalizeText(departamento)))
                .orElse(false);
    }

    public static String normalizeText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ");
        return normalized.trim().replaceAll("\\s+", " ");
    }

    public static List<Branch> sort(List<Branch> branches) {
        return branches.stream()
                .sorted(Comparator.comparing((Branch branch) -> normalizeText(branch.nombre()))
                        .thenComparing(branch -> normalizeText(branch.direccion()))
                        .thenComparing(Branch::id))
                .toList();
    }

    public static List<Branch> all() {
        return sort(BRANCHES);
    }

    private static Branch branch(String codigo, String departamento, String nombre, String direccion) {
        return new Branch("dac-" + codigo, codigo, departamento, nombre, direccion);
    }
}
