/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.matching.pattern;

import io.prestosql.matching.Captures;
import io.prestosql.matching.Match;
import io.prestosql.matching.Matcher;
import io.prestosql.matching.Pattern;
import io.prestosql.matching.PatternVisitor;
import io.prestosql.matching.Property;
import io.prestosql.matching.PropertyPattern;

import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class WithPattern<T>
        extends Pattern<T>
{
    private final PropertyPattern<? super T, ?, ?> propertyPattern;

    public WithPattern(PropertyPattern<? super T, ?, ?> propertyPattern, Pattern<T> previous)
    {
        super(previous);
        this.propertyPattern = requireNonNull(propertyPattern, "propertyPattern is null");
    }

    public Pattern<?> getPattern()
    {
        return propertyPattern.getPattern();
    }

    public Property<? super T, ?, ?> getProperty()
    {
        return propertyPattern.getProperty();
    }

    @Override
    public <C> Stream<Match> accept(Matcher matcher, Object object, Captures captures, C context)
    {
        return matcher.matchWith(this, object, captures, context);
    }

    @Override
    public void accept(PatternVisitor patternVisitor)
    {
        patternVisitor.visitWith(this);
    }
}