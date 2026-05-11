# Подробная инструкция: Загрузка кода на GitHub через командную строку

## Шаг 1: Установка Git (если не установлен)

### Проверьте, установлен ли Git:
1. Нажмите `Win + R`
2. Введите `cmd` и нажмите Enter
3. Введите команду:
   ```
   git --version
   ```
4. Если увидите версию (например `git version 2.40.0`) - Git установлен, переходите к Шагу 2

### Если Git не установлен:
1. Зайдите на https://git-scm.com/download/win
2. Скачайте установщик (64-bit Git for Windows Setup)
3. Запустите установщик
4. Нажимайте "Next" на всех шагах (можно оставить настройки по умолчанию)
5. Нажмите "Finish" в конце
6. Перезапустите командную строку

## Шаг 2: Создание репозитория на GitHub

### 2.1. Войдите в GitHub
1. Зайдите на https://github.com
2. Войдите в свой аккаунт

### 2.2. Создайте новый репозиторий
1. Нажмите кнопку "+" в правом верхнем углу
2. Выберите "New repository"
3. Заполните форму:
   - **Repository name**: `videochat` (или любое название на английском)
   - **Description**: можно оставить пустым
   - Выберите **Public** (публичный)
4. Нажмите кнопку **"Create repository"**

### 2.3. Скопируйте URL репозитория
1. После создания GitHub покажет вам URL
2. Он будет выглядеть примерно так:
   ```
   https://github.com/ВАШ_USERNAME/videochat.git
   ```
3. Скопируйте этот URL (нажмите на иконку копирования)

## Шаг 3: Настройка Git (первый раз)

### 3.1. Откройте командную строку
1. Нажмите `Win + R`
2. Введите `cmd` и нажмите Enter

### 3.2. Настройте имя пользователя
Введите команду (замените на ваше имя):
```
git config --global user.name "Ваше Имя"
```

### 3.3. Настройте email
Введите команду (замените на вашу почту):
```
git config --global user.email "ваша_почта@example.com"
```

## Шаг 4: Инициализация Git в папке проекта

### 4.1. Перейдите в папку проекта
В командной строке введите:
```
cd C:\Users\pirah\AndroidStudioProjects\videochat
```

**Проверка:**
- Введите `dir` чтобы увидеть файлы в папке
- Должны увидеть папки: `app`, `server`, файлы: `build.gradle.kts` и т.д.

### 4.2. Инициализируйте Git
Введите команду:
```
git init
```

**Что увидите:**
```
Initialized empty Git repository in C:/Users/pirah/AndroidStudioProjects/videochat/.git/
```

### 4.3. Добавьте все файлы
Введите команду:
```
git add .
```

**Что это делает:** Добавляет все файлы в Git (точка означает "все файлы")

### 4.4. Проверьте статус
Введите команду:
```
git status
```

**Что увидите:** Список всех файлов, которые будут добавлены (зеленые)

## Шаг 5: Создание первого коммита

### 5.1. Создайте коммит
Введите команду:
```
git commit -m "Initial commit"
```

**Что увидите:**
```
[main (root-commit) abc1234] Initial commit
 36 files changed, 1234 insertions(+)
```

**Что это делает:** Сохраняет текущее состояние файлов с сообщением "Initial commit"

## Шаг 6. Создание ветки main

### 6.1. Переименуйте ветку
Введите команду:
```
git branch -M main
```

**Что это делает:** Переименовывает ветку в `main` (современное название вместо `master`)

## Шаг 7. Подключение к GitHub

### 7.1. Добавьте удаленный репозиторий
Введите команду (замените на ваш URL):
```
git remote add origin https://github.com/ВАШ_USERNAME/videochat.git
```

**Пример:**
```
git remote add origin https://github.com/ivanov/videochat.git
```

### 7.2. Проверьте подключение
Введите команду:
```
git remote -v
```

**Что увидите:**
```
origin  https://github.com/ВАШ_USERNAME/videochat.git (fetch)
origin  https://github.com/ВАШ_USERNAME/videochat.git (push)
```

## Шаг 8. Загрузка кода на GitHub

### 8.1. Отправьте код на GitHub
Введите команду:
```
git push -u origin main
```

### 8.2. GitHub попросит войти
Появится окно входа в GitHub:
1. Введите ваш GitHub username
2. Введите ваш GitHub password
   - **ВАЖНО:** Обычный пароль не сработает!
   - Нужно использовать Personal Access Token (см. Шаг 9)

## Шаг 9: Создание Personal Access Token (если пароль не работает)

GitHub больше не принимает обычные пароли для командной строки. Нужно создать токен.

### 9.1. Зайдите в настройки GitHub
1. На GitHub нажмите на ваш аватар в правом верхнем углу
2. Выберите "Settings"
3. В левом меню выберите "Developer settings"
4. Выберите "Personal access tokens"
5. Выберите "Tokens (classic)"

### 9.2. Создайте новый токен
1. Нажмите кнопку "Generate new token" → "Generate new token (classic)"
2. Заполните:
   - **Note**: `videochat upload` (любое название)
   - **Expiration**: выберите дату (например 90 дней)
   - **Select scopes**: поставьте галочку на `repo` (это даст доступ к репозиториям)
3. Нажмите "Generate token"

### 9.3. Скопируйте токен
1. GitHub покажет токен (длинная строка символов)
2. **ВАЖНО:** Скопируйте его сразу! Он больше не появится!
3. Выглядит примерно так: `ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

### 9.4. Используйте токен как пароль
Когда команда `git push` попросит пароль:
- Username: ваш GitHub username
- Password: вставьте токен (не обычный пароль!)

## Шаг 10. Проверка успешной загрузки

### 10.1. В командной строке
После успешной загрузки увидите:
```
Enumerating objects: 45, done.
Counting objects: 100% (45/45), done.
...
To https://github.com/ВАШ_USERNAME/videochat.git
 * [new branch]      main -> main
```

### 10.2. На GitHub
1. Обновите страницу репозитория на GitHub
2. Должны увидеть все файлы вашего проекта

## Полный список команд (короткая версия)

```bash
# 1. Перейти в папку проекта
cd C:\Users\pirah\AndroidStudioProjects\videochat

# 2. Инициализировать Git
git init

# 3. Добавить все файлы
git add .

# 4. Создать коммит
git commit -m "Initial commit"

# 5. Переименовать ветку
git branch -M main

# 6. Подключить к GitHub (замените URL на ваш)
git remote add origin https://github.com/ВАШ_USERNAME/videochat.git

# 7. Загрузить на GitHub
git push -u origin main
```

## Как обновлять код на GitHub (после изменений)

### Если вы изменили код и хотите загрузить новые изменения:

```bash
# 1. Перейти в папку проекта
cd C:\Users\pirah\AndroidStudioProjects\videochat

# 2. Проверить что изменилось
git status

# 3. Добавить измененные файлы
git add .

# 4. Создать коммит с описанием изменений
git commit -m "Описание изменений"

# 5. Загрузить на GitHub
git push
```

## Возможные ошибки и решения

### Ошибка: "fatal: not a git repository"
**Причина:** Вы не в папке с Git или не инициализировали Git
**Решение:**
```bash
cd C:\Users\pirah\AndroidStudioProjects\videochat
git init
```

### Ошибка: "fatal: remote origin already exists"
**Причина:** Удаленный репозиторий уже добавлен
**Решение:** Удалите и добавьте заново:
```bash
git remote remove origin
git remote add origin https://github.com/ВАШ_USERNAME/videochat.git
```

### Ошибка: "Authentication failed"
**Причина:** Неверный логин/пароль
**Решение:** Используйте Personal Access Token вместо пароля (см. Шаг 9)

### Ошибка: "fatal: unable to access"
**Причина:** Проблемы с интернетом или блокировка
**Решение:**
1. Проверьте интернет
2. Если используете VPN, попробуйте отключить
3. Проверьте что URL правильный

### Ошибка: "Updates were rejected"
**Причина:** На GitHub есть изменения которых нет локально
**Решение:** Принудительная загрузка (осторожно!):
```bash
git push -f
```
Или сначала скачайте изменения:
```bash
git pull
```

## Полезные команды

### Посмотреть историю коммитов:
```
git log
```

### Посмотреть изменения в файлах:
```
git diff
```

### Отменить изменения в файле (если ошиблись):
```
git checkout -- имя_файла
```

### Удалить файл из Git (но оставить на компьютере):
```
git rm --cached имя_файла
```

## Краткая шпаргалка

**Первая загрузка:**
```bash
cd C:\Users\pirah\AndroidStudioProjects\videochat
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/ВАШ_USERNAME/videochat.git
git push -u origin main
```

**Последующие обновления:**
```bash
cd C:\Users\pirah\AndroidStudioProjects\videochat
git add .
git commit -m "Описание изменений"
git push
```

## Что дальше?

После загрузки на GitHub:
1. Зайдите на Railway
2. Создайте проект
3. Выберите репозиторий `videochat`
4. Следуйте инструкции из `RAILWAY_GUIDE.md`

Удачи! 🚀
