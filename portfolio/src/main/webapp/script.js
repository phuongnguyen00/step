// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * Adds a random greeting to the page.
 */
function addRandomGreeting() {
  const greetings =
      ['Hello world!', '¡Hola Mundo!', '你好，世界！', 'Bonjour le monde!'];

  // Pick a random greeting.
  const greeting = greetings[Math.floor(Math.random() * greetings.length)];

  // Add it to the page.
  const greetingContainer = document.getElementById('greeting-container');
  greetingContainer.innerText = greeting;
}

function addRandomFact() {
  const facts =
      ['I love brush lettering.', 'I enjoy playing badminton.', 'I love learning new things!', 'I would love to learn how to bake.'];

  // Pick a random fact.
  const fact = facts[Math.floor(Math.random() * facts.length)];

  // Add it to the page.
  const greetingContainer = document.getElementById('greeting-container');
  greetingContainer.innerText = fact;
  console.log(fact);
}

/**
 * Another way to use fetch is by using the async and await keywords. This
 * allows you to use the return values directly instead of going through
 * Promises.
 */
async function getHello() {
  const response = await fetch('/data');
  const hello = await response.text();
  document.getElementById('hello-container').innerHTML = hello;
}

/**
 * Fetches stats from the servers and adds them to the DOM.
 */
function getComments() {
  fetch('/data').then(response => response.json()).then((comments) => {
    // comments is the result of response.json()
    
    const commentsList = document.getElementById('comments-container');
    commentsList.innerHTML = '';
    
    //add the comments to the comment container
    comments.forEach((comment)=>{
        commentsList.appendChild(createCommentElement(comment))
    })
  });
}

/**
 * Access the data entity to get the email associated with the username ~ HashMap
 */

function getEmail(userName){
    fetch('/username-email?user-name='+userName).then(response => {
        return response;
    });
}

/** Creates an element that represents a task, including its delete button. */
function createCommentElementDelete(comment) {
  const commentElement = document.createElement('li');
  const textElement = document.createElement('span');
  textElement.innerText = comment.userName + " says: " + comment.text + " ";

  const deleteButtonElement = document.createElement('button');
  deleteButtonElement.innerText = 'Delete';
  deleteButtonElement.addEventListener('click', () => {
    deleteComment(comment);

    // Remove the task from the DOM.
    commentElement.remove();
  });

  commentElement.appendChild(textElement);
  commentElement.appendChild(deleteButtonElement);
  return commentElement;
}

/*
* Show the number of comments that is consistent with the login status of the user
*/
function getCommentsUpdatedShown(){
    fetch('/login-check').then(response => response.json()).then((loginInfo) => {
        const logIn = !! + loginInfo[0];
        const userName = loginInfo[1];
        const loggedInWithUserName = logIn && (Boolean(userName));
        this.getCommentsUpdated(loggedInWithUserName, userName);
    });
}
/**
 * Fetches stats from the servers and adds them to the DOM.
 */

function getCommentsUpdated(loggedInWithUserName, currentUserName) {
  let numComments = document.getElementById("comments-num").value;
  let sortingOrder = document.getElementById("sorting-cmt").value;

  fetch('/data?comments-num='+numComments+'&sorting-cmt='+sortingOrder).then(response => response.json()).then((comments) => {
    // comments is the result of response.json()
    const commentsList = document.getElementById('comments-container');
    commentsList.innerHTML = '';
    
    //display the number of comments that the user chooses or the maximum number of comments available.
    let numToIterate = Math.min(numComments, comments.length);
   
    //get the number of comments and show that to the user
    const numComShow = document.getElementById("num-cmt");
    numComShow.innerText = numToIterate;
    
    // depending whether the user is logged in, show delete button 
    if (!loggedInWithUserName) {
        for (let i = 0; i < numToIterate; i++ ){
            commentsList.appendChild(createCommentElement(comments[i]));
        }
    } else {
        for (let i = 0; i < numToIterate; i++ ){
            if (currentUserName === comments[i].userName) {
                console.log("I'm here, which means the user has the same name as this comment");
                console.log(comments[i].text);
                commentsList.appendChild(createCommentElementDelete(comments[i]));
            } else {
                commentsList.appendChild(createCommentElement(comments[i]));
            }
        }
    }
    
  });
}

/** Tells the server to delete one comment. */
function deleteComment(comment) {
  const params = new URLSearchParams();
  params.append('id', comment.id);
  fetch('/delete-comment', {method: 'POST', body: params});
}

/** Creates an <li> element containing text. */
function createListElement(text) {
  const liElement = document.createElement('li');
  liElement.innerText = text;
  return liElement;
}

/** Creates an element that represents a comment, without delete button. */
function createCommentElement(comment) {
  const commentElement = document.createElement('li');
  commentElement.innerText = comment.userName + " says: " + comment.text;
  return commentElement;
}

/** Tells the server to delete the comment and just get back an empty response. */
function deleteComments() {
  fetch('/delete-data').then(response => 
    //fetch has deleted everything using in delete-data
    //therefore, we just need to get the comments
    this.getComments()
  );
}

/**
 * Displays the form if and only if the user is logged in 
 */
function getLogin() {
  fetch('/login-check').then(response => response.json()).then((loginInfo) => {
    //loginInfo is an arrayList where the first element indicates whether the user is logged in,
    //and the second element tells the userName
    const greetingSection = document.getElementById('login-status');
    const greeting = document.createElement('p');
    const logIn = !! + loginInfo[0];
    const userName = loginInfo[1];
    let loggedInWithUserName = false;

    // Check if the user is logged in or not and display text accordingly
    if(!logIn) { 
        console.log("From js, I'm not logged in.");
        greeting.innerHTML = 'Want to post a comment? <a href="/login">Log in</a>.';
        document.getElementById('login-form').style.display = 'none';
    } else {
        console.log("Get to this step: logged in");
        if (!loginInfo[1]) {//if there is no userName
            greeting.innerHTML = "Welcome! You need to have a user name to post a comment. <br>";
            document.getElementById('login-form').style.display = 'none';
            document.getElementById('user-name-form').style.display = 'block';
        } else {
            greeting.innerHTML = 'Welcome back ' + userName+ '! ' + '<a href="/login">Log out</a> or ';
            greeting.innerHTML += '<a href="contact-form.html#comment-header" onclick= "changeUserName()"> Change your username<a>';
            greeting.innerHTML += '.';
            loggedInWithUserName = true;
        }  
    }
    greetingSection.appendChild(greeting);
  });
}

function changeUserName(){
    document.getElementById('user-name-form').style.display = 'block';
}

function onLoadFunction(){
    getLogin();
    getCommentsUpdatedShown();
}






