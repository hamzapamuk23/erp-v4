// Evaluates an SPDX license expression against an allowlist.
// AND binds tighter than OR (SPDX). A disjunction is allowed when any alternative is allowed; a
// conjunction only when every conjunct is. Anything unparsable is rejected.

const tokenize = (expression) =>
  expression.match(/\(|\)|[^\s()]+/g)?.map((token) => token.trim()) ?? []

export function isAllowedExpression(expression, allowed) {
  const tokens = tokenize(expression)
  let position = 0

  const peek = () => tokens[position]
  const take = () => tokens[position++]

  // Each parse function returns a boolean, or throws on a malformed expression.
  const parseOr = () => {
    let result = parseAnd()
    while (peek() === 'OR') {
      take()
      const next = parseAnd()
      result = result || next
    }
    return result
  }

  const parseAnd = () => {
    let result = parseAtom()
    while (peek() === 'AND') {
      take()
      const next = parseAtom()
      result = result && next
    }
    return result
  }

  const parseAtom = () => {
    const token = take()
    if (token === undefined || token === ')' || token === 'AND' || token === 'OR') {
      throw new SyntaxError(`Unexpected token in license expression: ${token ?? 'end'}`)
    }
    if (token === '(') {
      const inner = parseOr()
      if (take() !== ')') throw new SyntaxError('Unbalanced parentheses in license expression')
      return inner
    }
    // "<id> WITH <exception>" only narrows what the license demands, so the id decides.
    if (peek() === 'WITH') {
      take()
      if (take() === undefined) throw new SyntaxError('Missing license exception after WITH')
    }
    return allowed.has(token)
  }

  try {
    const result = parseOr()
    return position === tokens.length && result
  } catch (error) {
    if (error instanceof SyntaxError) return false
    throw error
  }
}
