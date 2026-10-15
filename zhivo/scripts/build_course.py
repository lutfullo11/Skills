#!/usr/bin/env python3
"""Build original authored curriculum. No downloaded lessons or random content."""
import hashlib
import json
import pathlib
import sys
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "content"))
LESSONS = []
WORDS = {}

def add(stage, slug, title, goal, theory, examples, words, choice, fill, order, listen, correction, reading, speak, topic="Каждый день", skill="Грамматика", strict_yo=False):
    lid = stage + "_" + slug
    ids = []
    for entry in words:
        word, meaning, example = entry[:3]
        english = entry[3] if len(entry) > 3 else ""
        key = unicodedata.normalize("NFC", word.replace("\u0301", "").lower()) + "|" + meaning.lower()
        wid = "w_" + hashlib.sha1(key.encode()).hexdigest()[:12]
        if wid not in WORDS:
            WORDS[wid] = dict(id=wid, word=word, meaning=meaning, example=example, english=english, lessonId=lid)
        ids.append(wid)
    tasks = []
    def task(kind, prompt, explanation, **fields):
        tasks.append(dict(id=f"{lid}_{len(tasks)+1}", type=kind, prompt=prompt, explanation=explanation, strictYo=strict_yo, **fields))
    p, answer, wrong1, wrong2, why = choice
    task("CHOICE", p, why, answers=[answer], options=[answer, wrong1, wrong2])
    p, answer, why = fill
    task("FILL", p, why, answers=answer.split("|"))
    sentence, why = order
    task("ORDER", "Соберите предложение из слов", why, answers=sentence.split("|"))
    audio, p, answer, wrong1, wrong2, why = listen
    task("LISTEN", p, why, audio=audio, answers=[answer], options=[answer, wrong1, wrong2])
    task("DICTATION", "Послушайте и запишите фразу", "Сравните слова и окончания с образцом. Пунктуация и регистр здесь не оцениваются.", audio=sentence.split("|")[0], answers=sentence.split("|"))
    task("MATCH", "Соедините слова и значения", "Слова запоминаются вместе со значением и примером употребления.", pairs=[[WORDS[i]["word"], WORDS[i]["meaning"]] for i in ids])
    wrong, answer, why = correction
    task("CORRECT", "Исправьте предложение: «" + wrong + "»", why, answers=answer.split("|"))
    passage, p, answer, wrong1, wrong2, why = reading
    task("READING", p, why, passage=passage, answers=[answer], options=[answer, wrong1, wrong2])
    task("SPEAK", speak, "Запишите себя и сравните с образцом. Самооценка речи не является автоматической проверкой произношения.", audio=sentence.split("|")[0])
    LESSONS.append(dict(id=lid, stage=stage, title=title, goal=goal, skill=skill, topic=topic,
                        theory=theory, examples=examples, words=ids, tasks=tasks, minutes=8))

from foundations import build as foundations
from everyday import build as everyday
from expression import build as expression
from conversations import dialogues
foundations(add)
everyday(add)
expression(add)
stages = [
    dict(id="start", title="Читаем и произносим", subtitle="Старт", description="Кириллица, ударение и первые речевые модели. Буквы изучаем в словах и понятных фразах."),
    dict(id="a1", title="Первые разговоры", subtitle="Практика A1", description="Знакомство, семья, покупки, время и базовые формы. Цель — решать простые повседневные задачи."),
    dict(id="a2", title="Общаемся каждый день", subtitle="Практика A2", description="Падежи, планы, движение, работа и просьбы. Учимся соединять реплики в связное сообщение."),
    dict(id="b1", title="Выражаем мысли", subtitle="Темы B1", description="События, причины, мнения и вежливое обсуждение. Развиваем понимание текста и точность формулировки."),
    dict(id="b2", title="Обсуждаем и аргументируем", subtitle="Темы B2", description="Аргументы, оттенки смысла, официальная речь и сложные тексты. Это тематическая практика, не подтверждение полного уровня B2.")]
course = dict(schema=1, stages=stages, lessons=LESSONS, words=list(WORDS.values()), dialogues=dialogues())
dest = ROOT / "app/src/main/assets/course.json"
dest.parent.mkdir(parents=True, exist_ok=True)
text = json.dumps(course, ensure_ascii=False, indent=2) + "\n"
if "--check" in sys.argv:
    assert dest.read_text() == text, "Rebuild course.json after changing authored content"
else:
    dest.write_text(text)
print(json.dumps({"lessons": len(LESSONS), "tasks": sum(len(l["tasks"]) for l in LESSONS), "words": len(WORDS), "dialogues": len(course["dialogues"])}, ensure_ascii=False))
