#let data = json("data.json")
#let solved = data.withAnswers

#let tick(checked) = if checked {
  box(stroke: 0.5pt, width: 10pt, height: 10pt, align(center)[x])
} else {
  box(stroke: 0.5pt, width: 10pt, height: 10pt)
}
#let blank(width) = box(width: width, stroke: (bottom: 0.5pt))[]
#let hint(body) = text(size: 9pt, fill: gray, body)

#let body-choice(b) = for option in b.options {
  let correct = solved and option.correct
  [#tick(correct) #h(4pt) #if correct { strong(option.text) } else { option.text }]
  parbreak()
}

#let body-true-false(b) = {
  let marked(value, label) = {
    let chosen = solved and b.correct == value
    [#tick(chosen) #h(4pt) #if chosen { strong(label) } else { label }]
  }
  [#marked(true, "True") #h(16pt) #marked(false, "False")]
  parbreak()
}

#let body-free-answer(b) = if solved {
  if b.answers.len() > 0 {
    [*Possible answers:*]
    list(..b.answers)
  }
} else {
  v(8pt)
  for _ in range(b.lines) {
    line(length: 100%, stroke: 0.5pt)
    v(16pt)
  }
}

#let gapped-text(parts) = {
  for part in parts {
    if not part.gap {
      part.text
    } else if solved {
      if part.number == none { strong(part.text) } else { strong(part.text + " (" + str(part.number) + ")") }
    } else {
      blank(part.width * 1cm)
    }
  }
  parbreak()
}

#let body-gaps(b) = if solved {
  if b.parts.len() > 0 {
    gapped-text(b.parts)
  } else if b.answers.len() > 0 {
    [*Gaps:* #b.answers.join(", ")]
    parbreak()
  }
} else {
  if b.words.len() > 0 {
    rect(fill: luma(240), radius: 4pt, inset: 8pt, text(size: 9pt)[
      *Words:* #b.words.enumerate().map(((i, word)) => [(#(i + 1)) #word]).join([ #h(8pt) | #h(8pt) ])
    ])
  }
  if b.parts.len() > 0 {
    gapped-text(b.parts)
  } else if b.words.len() == 0 {
    for i in range(1, b.gapCount + 1) {
      [Gap #i: #blank(60%)]
      parbreak()
    }
  }
}

#let body-connect(b) = if solved {
  list(..b.pairs.map(pair => [#pair.left → #pair.right]))
} else {
  grid(
    columns: (auto, 4cm, auto),
    row-gutter: 14pt,
    column-gutter: 6pt,
    [*Left*], [], [*Right*],
    ..b.pairs.map(pair => ([#pair.left], [], [#pair.right])).flatten(),
  )
}

#let body-ordering(b) = if solved {
  enum(..b.items)
} else {
  for item in b.items {
    [#tick(false) #h(4pt) #item]
    parbreak()
  }
}

#let body-image-text(b) = if solved {
  [*Answer:* #b.answer]
  parbreak()
} else {
  v(8pt)
  line(length: 100%, stroke: 0.5pt)
  v(12pt)
  line(length: 100%, stroke: 0.5pt)
}

#let body-enumeration(b) = if solved {
  strong[Possible answers (#b.requiredCount asked for):]
  enum(..b.answers)
  if b.ordered { hint[The order matters.] }
} else {
  if b.ordered {
    hint[Mind the order!]
    parbreak()
  }
  v(4pt)
  for i in range(1, b.requiredCount + 1) {
    [#(str(i) + ".") #blank(1fr)]
    v(12pt)
  }
}

#let body(b) = {
  if b.kind == "choice" { body-choice(b) }
  else if b.kind == "trueFalse" { body-true-false(b) }
  else if b.kind == "freeAnswer" { body-free-answer(b) }
  else if b.kind == "gaps" { body-gaps(b) }
  else if b.kind == "connect" { body-connect(b) }
  else if b.kind == "ordering" { body-ordering(b) }
  else if b.kind == "imageText" { body-image-text(b) }
  else if b.kind == "enumeration" { body-enumeration(b) }
}

#let sheet-title = if solved [#data.title - Solutions] else [#data.title]

#set document(title: data.title)
#set page(
  paper: "a4",
  margin: (top: 2.5cm, bottom: 2cm, left: 2cm, right: 2cm),
  header: align(right)[#sheet-title],
  footer: context align(center)[#counter(page).display()],
)
#set text(font: "Liberation Sans", size: 11pt, lang: "en")
#set par(justify: true)

#heading(level: 1)[#sheet-title]

#if solved [
  #align(right)[*Total: #data.totalPoints points*]
] else [
  #grid(
    columns: (1fr, 1fr, auto),
    gutter: 12pt,
    [*Name:* #blank(1fr)],
    [*Date:* #blank(1fr)],
    [*Total:* #h(4pt) #blank(1cm) \/ #data.totalPoints pt],
  )
]

#for section in data.sections [
  #heading(level: 2)[#section.title #h(1fr) #hint[#section.points points]]

  #for question in section.questions [
    #block(breakable: false)[
      #table(
        columns: (1fr, auto),
        stroke: none,
        inset: 0pt,
        [#strong(str(question.number) + ".") #question.title],
        if solved [#hint[#question.points pt]] else [#blank(0.8cm) #text(size: 9pt)[\/ #question.points pt]],
      )

      #if question.description != "" [
        #hint(question.description)

      ]
      #if question.image != none [
        #image(question.image, width: 40%)

      ]
      #body(question.body)
    ]

  ]

  #if not solved [
    #align(right)[#text(size: 10pt)[*Section:* #blank(1cm) *\/* *#section.points points*]]

  ]
]
