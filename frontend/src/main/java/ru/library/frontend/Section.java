package ru.library.frontend;

enum Section {
    CATALOG("Каталог", Icon.BOOK),
    READERS("Читатели", Icon.PEOPLE),
    CIRCULATION("Выдачи и возвраты", Icon.CIRCULATION),
    FINES("Штрафы", Icon.FINES),
    DICTIONARIES("Справочники", Icon.FOLDER),
    USERS("Сотрудники", Icon.PEOPLE),
    SETTINGS("Настройки", Icon.SETTINGS);

    final String title;
    final Icon icon;

    Section(String title, Icon icon) {
        this.title = title;
        this.icon = icon;
    }
}
